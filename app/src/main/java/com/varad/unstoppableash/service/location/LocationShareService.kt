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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Foreground service (type `location`) that runs [LocationShareEngine] while I'm sharing. It shows an ongoing
 * notification for as long as it runs — sharing is never invisible — with a one-tap "Pause 1 h".
 */
class LocationShareService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: Job? = null
    private var hostAcquired = false

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
        // Android refuses a location FGS started from the background (e.g. a restart); then we simply don't run.
        val started = runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        }.isSuccess
        if (!started) { stopSelf(); return START_NOT_STICKY }
        running.value = true
        if (engine == null) {
            val e = LocationShareEngine(
                container.pairs, container.presence, container.location, container.trips, container.events,
                FusedLocationUpdates(this), batteryLevels(), container.clock,
            )
            engine = scope.launch { e.run() }
            // Sharing keeps AshGo running anyway, so it also carries alerts (nudges, check-ins) on this phone.
            container.notificationHost.acquire(); hostAcquired = true
            scope.launch { container.settings.update { it.copy(sharingActive = true) } }
            scope.launch { stopWhenNotSharing() }
        }
        // Not sticky: a system restart would be a background start, which Android refuses for location. The app
        // restarts sharing when it's next opened, and after a reboot it says so (BootReceiver).
        return START_NOT_STICKY
    }

    /**
     * Stops (and the notification goes) the moment this phone shouldn't share: signed out, out of the orbit, off,
     * an open-ended pause, or sharing no longer chosen on this phone. collectLatest so a sign-out is never stuck
     * behind the old orbit's listener (review 8 I1).
     */
    private suspend fun stopWhenNotSharing() {
        val container = (application as AshGoApplication).container
        combine(container.pairs.pairState(), container.settings.settings.map { it.shareFromThisPhone }) { s, chosen -> s to chosen }
            .collectLatest { (state, chosen) ->
                val p = state as? PairState.Paired
                if (p == null || !chosen) { stopSelf(); return@collectLatest }
                container.presence.snapshot(p.meta.pairId, p.myUid).collect { s ->
                    if (!SharingPlan.runHere(true, s.sharing ?: SharingMode.OFF, s.sharingUntil, chosen)) stopSelf()
                }
            }
    }

    override fun onDestroy() {
        running.value = false
        val container = (application as AshGoApplication).container
        if (hostAcquired) { container.notificationHost.release(); hostAcquired = false }
        container.appScope.launch { container.settings.update { it.copy(sharingActive = false) } }
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
        /** In this process only: whether the service is actually running now (persisted flags survive death; this doesn't). */
        val running = kotlinx.coroutines.flow.MutableStateFlow(false)
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
