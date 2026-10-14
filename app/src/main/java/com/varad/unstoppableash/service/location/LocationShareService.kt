package com.varad.unstoppableash.service.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.varad.unstoppableash.AshGoApplication
import com.varad.unstoppableash.R
import com.varad.unstoppableash.domain.location.PauseChoice
import com.varad.unstoppableash.domain.location.SharingPlan
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.model.SharingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Foreground service (type `location`) that runs [LocationShareEngine] while I'm sharing. It shows an ongoing
 * notification for as long as it runs — sharing is never invisible — with a one-tap "Pause 1 h".
 */
class LocationShareService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as AshGoApplication).container
        if (intent?.action == ACTION_PAUSE) {
            scope.launch {
                val p = container.pairs.pairState().first() as? PairState.Paired
                if (p != null) container.presence.setSharing(p.meta.pairId, p.myUid, SharingMode.PAUSED, SharingPlan.pauseUntil(PauseChoice.ONE_HOUR, container.clock.now(), ZoneId.systemDefault()))
            }
            return START_NOT_STICKY
        }
        if (!hasLocationPermission(this)) { stopSelf(); return START_NOT_STICKY }
        ensureChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        if (engine == null) {
            val e = LocationShareEngine(
                container.pairs, container.presence, container.location, container.trips, container.events,
                FusedLocationUpdates(this), batteryLevels(), container.clock,
            )
            engine = scope.launch { e.run() }
            scope.launch { stopWhenNotSharing() }
        }
        return START_STICKY
    }

    /** Off, or paused with no end time: nothing for this service to do, so the notification goes too. */
    private suspend fun stopWhenNotSharing() {
        val container = (application as AshGoApplication).container
        container.pairs.pairState().collect { state ->
            val p = state as? PairState.Paired ?: return@collect stopSelf()
            container.presence.snapshot(p.meta.pairId, p.myUid).first { s ->
                s.sharing == SharingMode.OFF || s.sharing == null || (s.sharing == SharingMode.PAUSED && s.sharingUntil == null)
            }
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun batteryLevels(): Flow<BatteryLevel?> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                trySend(if (level >= 0 && scale > 0) BatteryLevel(level * 100 / scale, charging) else null)
            }
        }
        ContextCompat.registerReceiver(this@LocationShareService, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { unregisterReceiver(receiver) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_service), NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_orbit_glyph)
        .setContentTitle(getString(R.string.sharing_notification_title))
        .setContentText(getString(R.string.sharing_notification_body))
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .addAction(
            0, getString(R.string.sharing_pause_1h),
            PendingIntent.getService(this, 1, Intent(this, LocationShareService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE),
        )
        .build()

    companion object {
        const val CHANNEL = "service"
        private const val NOTIFICATION_ID = 41
        private const val ACTION_PAUSE = "com.varad.unstoppableash.PAUSE_SHARING"

        fun hasLocationPermission(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** Call from the foreground only (Android 14 forbids starting a location FGS from the background). */
        fun start(context: Context) {
            if (!hasLocationPermission(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, LocationShareService::class.java)) }
        }
    }
}
