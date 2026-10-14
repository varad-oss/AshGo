package com.varad.unstoppableash.service.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.varad.unstoppableash.AshGoApplication
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.notify.NotificationPlanner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** "I'm safe" and "+15 min" on my overdue-trip notification, without opening the app. */
class TripActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val container = (context.applicationContext as AshGoApplication).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                val p = withTimeoutOrNull(5_000) { container.pairs.pairState().first() } as? PairState.Paired ?: return@launch
                val trip = withTimeoutOrNull(5_000) { container.trips.observe(p.meta.pairId, p.myUid).first() }
                when (action) {
                    ARRIVE -> container.trips.arrive(p.meta.pairId, p.myUid)
                    EXTEND -> {
                        val now = container.clock.now()
                        val deadline = trip?.extendedTo ?: trip?.expectBy ?: now
                        container.trips.extend(p.meta.pairId, p.myUid, maxOf(deadline, now) + 15 * 60_000L)
                    }
                }
                NotificationManagerCompat.from(context).cancel(NotificationPlanner.KEY_MY_OVERDUE, AndroidNotifier.ID)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ARRIVE = "com.varad.unstoppableash.TRIP_ARRIVE"
        const val EXTEND = "com.varad.unstoppableash.TRIP_EXTEND"

        fun intent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context, action.hashCode(), Intent(context, TripActionReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
