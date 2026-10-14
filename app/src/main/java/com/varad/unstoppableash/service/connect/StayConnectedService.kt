package com.varad.unstoppableash.service.connect

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.varad.unstoppableash.AshGoApplication
import com.varad.unstoppableash.R
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.notify.Channel
import com.varad.unstoppableash.service.notify.Channels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The always-on link to the orbit (FGS `specialUse`): with no push server on the Spark plan, this is what lets a
 * partner's SOS, overdue trip or message reach this phone. It keeps presence honest (online + onDisconnect,
 * battery on 5 % changes) and runs the NotificationEngine. Stops itself when there is no orbit.
 */
class StayConnectedService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    private var hostAcquired = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Channels.create(this)
        val notification = NotificationCompat.Builder(this, Channel.SERVICE.id)
            .setSmallIcon(R.drawable.ic_orbit_glyph)
            .setContentTitle(getString(R.string.n_connected_title))
            .setContentText(getString(R.string.n_connected_body))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
            )
        }.onFailure { stopSelf(); return START_NOT_STICKY }
        _running.value = true
        if (!started) {
            started = true
            scope.launch { run() }
        }
        return START_STICKY
    }

    private suspend fun run() {
        val c = (application as AshGoApplication).container
        // One engine per process, shared with the app on screen and location sharing (never two posting).
        c.notificationHost.acquire(); hostAcquired = true
        // Only while there's something to watch (owner's ask); otherwise clearing AshGo really stops it.
        scope.launch {
            c.backgroundWatch.needed().collectLatest { need ->
                c.settings.update { it.copy(keepConnected = need) }
                if (!need) { delay(NOT_NEEDED_GRACE_MS); stopSelf() }
            }
        }
        c.pairs.pairState()
            .distinctUntilChanged { a, b -> a is PairState.Paired && b is PairState.Paired && a.meta.pairId == b.meta.pairId && a.myUid == b.myUid || (a !is PairState.Paired && a == b) }
            .collectLatest { state ->
                StopPolicy.stopAfterMs(state)?.let { delay(it); stopSelf(); return@collectLatest }
                val p = state as PairState.Paired
                coroutineScope {
                    // onDisconnect handlers are consumed when the connection drops: register again on every reconnect.
                    launch { c.rtdb.connected().filter { it }.collect { c.presence.goOnline(p.meta.pairId, p.myUid) } }
                    launch { batteryLevels().collect { (pct, charging) -> c.presence.reportBattery(p.meta.pairId, p.myUid, pct, charging) } }
                }
            }
    }

    private fun batteryLevels() = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                if (level >= 0 && scale > 0) trySend(level * 100 / scale to charging)
            }
        }
        ContextCompat.registerReceiver(this@StayConnectedService, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { unregisterReceiver(receiver) }
    }

    override fun onDestroy() {
        _running.value = false
        if (hostAcquired) { (application as AshGoApplication).container.notificationHost.release(); hostAcquired = false }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        /** A trip ending and a new one starting moments later shouldn't bounce the service. */
        private const val NOT_NEEDED_GRACE_MS = 60_000L
        private val _running = MutableStateFlow(false)
        /** Whether alerts can reach this phone with AshGo closed (Reliability shows it). */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        /** From the foreground, boot, or a heartbeat; Android may refuse a background start — that's not an error. */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, StayConnectedService::class.java)) }
        }
    }
}
