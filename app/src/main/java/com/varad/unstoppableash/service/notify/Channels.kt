package com.varad.unstoppableash.service.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.varad.unstoppableash.R
import com.varad.unstoppableash.domain.notify.Channel

/** Creates every channel AshGo posts to, at start-up (B-1: never "No Channel found"). Idempotent. */
object Channels {
    fun create(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // v2.0's nudge channel had a normal sound; nudges now beep on the alarm stream instead (LoudNudge).
        runCatching { nm.deleteNotificationChannel("nudges") }
        Channel.entries.forEach { c -> nm.createNotificationChannel(channelFor(context, c)) }
    }

    private fun channelFor(context: Context, c: Channel): NotificationChannel = when (c) {
        Channel.SOS -> NotificationChannel(c.id, context.getString(R.string.channel_sos), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_sos_desc)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            setBypassDnd(true) // honoured only if the person grants AshGo Do Not Disturb access
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        Channel.OVERDUE -> NotificationChannel(c.id, context.getString(R.string.channel_overdue), NotificationManager.IMPORTANCE_HIGH)
        Channel.CHAT -> NotificationChannel(c.id, context.getString(R.string.channel_chat), NotificationManager.IMPORTANCE_DEFAULT)
        Channel.CHECKINS -> NotificationChannel(c.id, context.getString(R.string.channel_checkins), NotificationManager.IMPORTANCE_DEFAULT)
        // Silent on purpose: the nudge's own beep plays on the alarm stream, so it is heard even in silent mode.
        Channel.NUDGES -> NotificationChannel(c.id, context.getString(R.string.channel_nudges), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
        }
        Channel.SERVICE -> NotificationChannel(c.id, context.getString(R.string.channel_service), NotificationManager.IMPORTANCE_LOW)
    }
}
