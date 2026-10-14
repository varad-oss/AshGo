package com.varad.unstoppableash.service.sos

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.varad.unstoppableash.domain.model.SmsStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** One aggregated result per message attempt (SosStateMachine's contract). */
data class SmsResult(val sosId: String, val contactId: String, val status: SmsStatus, val failure: String? = null)

/** The only way AshGo sends SMS. Tests use a fake; nothing automated ever reaches a real carrier. */
interface SmsDispatcher {
    /** SEND_SMS granted and the phone can send SMS at all. */
    fun canSend(): Boolean
    /** Hands the text to the system. False means it was refused before sending (reported as FAILED too). */
    fun send(sosId: String, contactId: String, number: String, text: String): Boolean
    val results: SharedFlow<SmsResult>
}

/** Multipart send with per-part sent/delivered PendingIntents → [SmsResultReceiver] → [SmsAttemptTracker]. */
class AndroidSmsDispatcher(private val context: Context) : SmsDispatcher {

    override fun canSend(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    override val results: SharedFlow<SmsResult> get() = Hub.results.asSharedFlow()

    override fun send(sosId: String, contactId: String, number: String, text: String): Boolean {
        val attempt = "$sosId|$contactId|${System.nanoTime()}"
        return try {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            val parts = sms.divideMessage(text)
            Hub.tracker.start(attempt, parts.size)
            val sent = ArrayList<PendingIntent>(parts.size)
            val delivered = ArrayList<PendingIntent>(parts.size)
            parts.indices.forEach { i ->
                sent += pending(attempt, sosId, contactId, i, ACTION_SENT)
                delivered += pending(attempt, sosId, contactId, i, ACTION_DELIVERED)
            }
            sms.sendMultipartTextMessage(number, null, parts, sent, delivered)
            true
        } catch (e: Exception) {
            // Number never logged.
            Log.w(TAG, "SMS refused: ${e.javaClass.simpleName}")
            Hub.results.tryEmit(SmsResult(sosId, contactId, SmsStatus.FAILED, "refused"))
            false
        }
    }

    private fun pending(attempt: String, sosId: String, contactId: String, part: Int, action: String): PendingIntent {
        val intent = Intent(context, SmsResultReceiver::class.java).setAction(action)
            .putExtra(EXTRA_ATTEMPT, attempt).putExtra(EXTRA_SOS, sosId).putExtra(EXTRA_CONTACT, contactId).putExtra(EXTRA_PART, part)
        // A distinct request code per part keeps the extras from being merged by PendingIntent identity.
        val code = (attempt + action + part).hashCode()
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
    }

    /** Process-wide, because results arrive in a broadcast receiver, not in the caller. */
    internal object Hub {
        val tracker = SmsAttemptTracker()
        val results = MutableSharedFlow<SmsResult>(extraBufferCapacity = 64)
    }

    internal companion object {
        const val TAG = "AshGoSms"
        const val ACTION_SENT = "com.varad.unstoppableash.SMS_SENT"
        const val ACTION_DELIVERED = "com.varad.unstoppableash.SMS_DELIVERED"
        const val EXTRA_ATTEMPT = "attempt"
        const val EXTRA_SOS = "sos"
        const val EXTRA_CONTACT = "contact"
        const val EXTRA_PART = "part"
    }
}

class SmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val attempt = intent.getStringExtra(AndroidSmsDispatcher.EXTRA_ATTEMPT) ?: return
        val sosId = intent.getStringExtra(AndroidSmsDispatcher.EXTRA_SOS) ?: return
        val contactId = intent.getStringExtra(AndroidSmsDispatcher.EXTRA_CONTACT) ?: return
        val part = intent.getIntExtra(AndroidSmsDispatcher.EXTRA_PART, -1)
        val ok = resultCode == android.app.Activity.RESULT_OK
        val result = when (intent.action) {
            AndroidSmsDispatcher.ACTION_SENT -> if (ok) SmsPart.SENT else SmsPart.FAILED
            // A delivery report only ever confirms; a missing or odd report is "not confirmed", not "failed".
            AndroidSmsDispatcher.ACTION_DELIVERED -> if (ok) SmsPart.DELIVERED else return
            else -> return
        }
        val status = AndroidSmsDispatcher.Hub.tracker.record(attempt, part, result) ?: return
        val failure = if (status == SmsStatus.FAILED) failureText(resultCode) else null
        AndroidSmsDispatcher.Hub.results.tryEmit(SmsResult(sosId, contactId, status, failure))
    }

    private fun failureText(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_NO_SERVICE -> "no signal"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "radio off"
        SmsManager.RESULT_ERROR_NULL_PDU, SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "carrier error"
        else -> "not sent"
    }
}
