package com.varad.unstoppableash.service.connect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.varad.unstoppableash.AshGoApplication
import com.varad.unstoppableash.R
import com.varad.unstoppableash.V2Activity
import com.varad.unstoppableash.domain.notify.Channel
import com.varad.unstoppableash.service.notify.AndroidNotifier
import com.varad.unstoppableash.service.notify.Channels
import com.varad.unstoppableash.service.notify.safeNotify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * After a reboot or an update: reconnect, and — because Android won't let location sharing restart on its own —
 * say so plainly instead of silently not sharing.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = (context.applicationContext as AshGoApplication).container
        if (container.auth.current() == null) return
        Channels.create(context)
        StayConnectedService.start(context)
        HeartbeatWorker.schedule(context)
        val pending = goAsync()
        container.appScope.launch {
            try {
                if (container.settings.settings.first().sharingActive) {
                    val open = android.app.PendingIntent.getActivity(
                        context, 20, Intent(context, V2Activity::class.java).putExtra(AndroidNotifier.EXTRA_OPEN, AndroidNotifier.OPEN_MAP),
                        android.app.PendingIntent.FLAG_IMMUTABLE,
                    )
                    val n = NotificationCompat.Builder(context, Channel.SERVICE.id)
                        .setSmallIcon(R.drawable.ic_orbit_glyph)
                        .setContentTitle(context.getString(R.string.n_resume_sharing_title))
                        .setContentText(context.getString(R.string.n_resume_sharing_body))
                        .setContentIntent(open).setAutoCancel(true)
                        .build()
                    safeNotify(context, "resume-sharing", AndroidNotifier.ID, n)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
