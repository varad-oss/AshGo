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

    override fun canSend(): Boolean = hasSmsHardware() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    // FEATURE_TELEPHONY_MESSAGING only exists from API 33; older phones never declare it, so asking for it
    // there would switch SOS SMS off on every Android 8–12 phone (lint InlinedApi caught this).
    private fun hasSmsHardware(): Boolean = context.packageManager.hasSystemFeature(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) PackageManager.FEATURE_TELEPHONY_MESSAGING else PackageManager.FEATURE_TELEPHONY,
    )

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
        // Delivery reports carry their real status in fill-in extras ("pdu", "format"), which only a mutable
        // PendingIntent receives, and the network may report "still trying" before the final report, so it
        // must not be one-shot. The intent is explicit to our own receiver, so mutability exposes nothing.
        val flags = if (action == ACTION_DELIVERED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        } else {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        }
        return PendingIntent.getBroadcast(context, requestCodes.incrementAndGet(), intent, flags)
    }

    /** Process-wide, because results arrive in a broadcast receiver, not in the caller. */
    /** Unique per PendingIntent so extras are never merged with another part's (hash codes can collide). */
    private val requestCodes = java.util.concurrent.atomic.AtomicInteger((System.currentTimeMillis() % 1_000_000).toInt() * 100)

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
            // The broadcast's resultCode is always OK for delivery reports; the truth is the TP-Status in the PDU.
            AndroidSmsDispatcher.ACTION_DELIVERED -> SmsPart.fromDeliveryStatus(deliveryStatus(intent)) ?: return
            else -> return
        }
        val status = AndroidSmsDispatcher.Hub.tracker.record(attempt, part, result) ?: return
        val failure = when {
            status != SmsStatus.FAILED -> null
            intent.action == AndroidSmsDispatcher.ACTION_DELIVERED -> "not delivered"
            else -> failureText(resultCode)
        }
        AndroidSmsDispatcher.Hub.results.tryEmit(SmsResult(sosId, contactId, status, failure))
    }

    private fun deliveryStatus(intent: Intent): Int? = runCatching {
        val pdu = intent.getByteArrayExtra("pdu") ?: return null
        val format = intent.getStringExtra("format")
        android.telephony.SmsMessage.createFromPdu(pdu, format)?.status
    }.getOrNull()

    private fun failureText(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_NO_SERVICE -> "no signal"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "radio off"
        SmsManager.RESULT_ERROR_NULL_PDU, SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "carrier error"
        else -> "not sent"
    }
}
