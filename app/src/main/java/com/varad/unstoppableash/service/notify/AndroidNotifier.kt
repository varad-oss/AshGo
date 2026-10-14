package com.varad.unstoppableash.service.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.varad.unstoppableash.R
import com.varad.unstoppableash.V2Activity
import com.varad.unstoppableash.domain.notify.NotificationKind
import com.varad.unstoppableash.domain.notify.PlannedNotification
import com.varad.unstoppableash.feature.safety.PartnerSosActivity

/** Builds AshGo's notifications. Message text is hidden on the lock screen; safety alerts are not. */
class AndroidNotifier(private val context: Context) : Notifier {

    override fun post(n: PlannedNotification, partnerName: String): Boolean {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return false // Reliability shows this; the engine tries again later
        val b = NotificationCompat.Builder(context, n.kind.channel.id)
            .setSmallIcon(R.drawable.ic_orbit_glyph)
            .setAutoCancel(true)
        when (n.kind) {
            NotificationKind.PARTNER_SOS -> {
                val open = activityIntent(PartnerSosActivity::class.java, null, requestCode = 10)
                b.setContentTitle(context.getString(R.string.n_sos_title, partnerName))
                    .setContentText(context.getString(R.string.n_sos_body))
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(true).setAutoCancel(false)
                    .setContentIntent(open)
                // Full screen over the lock screen where Android allows it; otherwise a heads-up alert.
                if (canUseFullScreenIntent()) b.setFullScreenIntent(open, true)
            }
            NotificationKind.PARTNER_OVERDUE -> b
                .setContentTitle(context.getString(R.string.n_partner_overdue_title, partnerName))
                .setContentText(context.getString(R.string.n_partner_overdue_body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(activityIntent(V2Activity::class.java, OPEN_MAP, 11))
            NotificationKind.MY_OVERDUE -> b
                .setContentTitle(context.getString(R.string.n_my_overdue_title))
                .setContentText(context.getString(R.string.n_my_overdue_body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(activityIntent(V2Activity::class.java, OPEN_MAP, 12))
                // Ending or extending a trip from the lock screen needs an unlock, like cancelling an SOS (review I5).
                .addAction(tripAction(R.string.overdue_me_safe, TripActionReceiver.ARRIVE))
                .addAction(tripAction(R.string.trip_extend, TripActionReceiver.EXTEND))
                // "Get help" only opens Safety: sending SOS always needs the hold.
                .addAction(0, context.getString(R.string.overdue_me_help), activityIntent(V2Activity::class.java, OPEN_SAFETY, 13))
            NotificationKind.CHAT -> {
                val body = if (n.count > 1) context.resources.getQuantityString(R.plurals.n_chat_many, n.count, n.count)
                else n.text ?: context.getString(R.string.n_photo)
                b.setContentTitle(context.getString(R.string.n_chat_one, partnerName))
                    .setContentText(body)
                    .setNumber(n.count)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(
                        NotificationCompat.Builder(context, n.kind.channel.id).setSmallIcon(R.drawable.ic_orbit_glyph)
                            .setContentTitle(context.getString(R.string.n_chat_public, partnerName)).build(),
                    )
                    .setContentIntent(activityIntent(V2Activity::class.java, OPEN_CHAT, 14))
            }
            NotificationKind.CHECKIN -> b
                .setContentTitle(context.getString(R.string.n_checkin_title, partnerName))
                .setContentText(KindText.of(context, n.detail.orEmpty(), n.text))
                .setContentIntent(activityIntent(V2Activity::class.java, OPEN_HOME, 15))
            NotificationKind.NUDGE -> b
                .setContentTitle(context.getString(R.string.n_nudge_title, partnerName))
                .setContentText(KindText.of(context, n.detail.orEmpty(), n.text))
                .setContentIntent(activityIntent(V2Activity::class.java, OPEN_CHAT, 16))
        }
        return safeNotify(context, n.tag, ID, b.build())
    }

    /** On Android 12+ the system asks for an unlock first; older versions open AshGo instead of acting from the lock screen. */
    private fun tripAction(label: Int, action: String): NotificationCompat.Action =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            NotificationCompat.Action.Builder(0, context.getString(label), TripActionReceiver.intent(context, action))
                .setAuthenticationRequired(true).build()
        } else {
            NotificationCompat.Action.Builder(0, context.getString(label), activityIntent(V2Activity::class.java, OPEN_MAP, if (action == TripActionReceiver.ARRIVE) 17 else 18)).build()
        }

    override fun showing(): Set<String>? = runCatching {
        context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.mapNotNull { it.tag }.toSet()
    }.getOrNull()

    override fun cancel(tag: String) = NotificationManagerCompat.from(context).cancel(tag, ID)

    /** A local notification for the Reliability screen's "Send a test notification". */
    fun postTest() {
        val b = NotificationCompat.Builder(context, com.varad.unstoppableash.domain.notify.Channel.CHECKINS.id)
            .setSmallIcon(R.drawable.ic_orbit_glyph)
            .setContentTitle(context.getString(R.string.n_test_title))
            .setContentText(context.getString(R.string.n_test_body))
            .setAutoCancel(true)
        safeNotify(context, "test", ID, b.build())
    }

    private fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true

    private fun activityIntent(target: Class<*>, open: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        open?.let { intent.putExtra(EXTRA_OPEN, it) }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val ID = 7
        const val EXTRA_OPEN = "open"
        const val OPEN_CHAT = "chat"
        const val OPEN_MAP = "map"
        const val OPEN_SAFETY = "safety"
        const val OPEN_HOME = "home"
    }
}
