package com.varad.unstoppableash.service.location

import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.events.EventRepository
import com.varad.unstoppableash.data.location.LocationRepository
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.presence.PresenceRepository
import com.varad.unstoppableash.data.trips.TripRepository
import com.varad.unstoppableash.domain.location.ArrivalDetector
import com.varad.unstoppableash.domain.location.CadencePolicy
import com.varad.unstoppableash.domain.location.LocationCadence
import com.varad.unstoppableash.domain.location.SharingPlan
import com.varad.unstoppableash.domain.location.distanceMeters
import com.varad.unstoppableash.domain.model.LocationFix
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.model.SharingMode
import com.varad.unstoppableash.domain.model.Trip
import com.varad.unstoppableash.domain.model.TripState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

data class BatteryLevel(val pct: Int, val charging: Boolean)

/** A stream of fixes at the given cadence; the real one is FusedLocationProvider in LocationShareService. */
fun interface LocationUpdates {
    fun updates(cadence: LocationCadence): Flow<LocationFix>
}

/**
 * What the location service does, without Android (J6, D-14). While my sharing is effectively LIVE every fix
 * is written; paused or off, nothing is (the last point stays and shows as paused). A timed pause ends by
 * itself. On a trip with a destination, arrival is detected here and reported once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationShareEngine(
    private val pairs: PairRepository,
    private val presence: PresenceRepository,
    private val location: LocationRepository,
    private val trips: TripRepository,
    private val events: EventRepository,
    private val updates: LocationUpdates,
    private val battery: Flow<BatteryLevel?>,
    private val clock: Clock,
    private val tickMs: Long = 30_000,
) {
    private data class Plan(val cadence: LocationCadence?, val trip: Trip?)

    /** Runs until cancelled, following whichever orbit I'm in. */
    suspend fun run() {
        pairs.pairState().map { it as? PairState.Paired }
            .distinctUntilChanged { a, b -> a?.meta?.pairId == b?.meta?.pairId && a?.myUid == b?.myUid }
            .collectLatest { p -> log("orbit ${p != null}"); if (p != null) runFor(p.meta.pairId, p.myUid) }
    }

    private suspend fun runFor(pid: String, uid: String) = coroutineScope {
        val sideEffects = this
        var expiredPause: Long? = null
        val plans = combine(
            presence.snapshot(pid, uid),
            trips.observe(pid, uid).onStart { emit(null) },
            battery.onStart { emit(null) },
            ticker(),
        ) { me, trip, batt, _ ->
            val mode = me.sharing ?: SharingMode.OFF
            val effective = SharingPlan.effective(mode, me.sharingUntil, clock.now())
            if (mode == SharingMode.PAUSED && effective == SharingMode.LIVE && expiredPause != me.sharingUntil) {
                expiredPause = me.sharingUntil
                launch { presence.setSharing(pid, uid, SharingMode.LIVE, null) }
            }
            // Overdue is still a trip in progress: keep the trip cadence and arrival detection exactly then.
            val active = trip?.takeIf { it.state == TripState.ACTIVE || it.state == TripState.OVERDUE }
            Plan(CadencePolicy.cadenceFor(effective, active != null, batt?.pct, batt?.charging), active)
        }.distinctUntilChanged().shareIn(this, SharingStarted.Eagerly, replay = 1) // one set of listeners for both uses

        var currentTrip: Trip? = null
        val arrival = ArrivalTracker()
        launch { plans.collect { currentTrip = it.trip } }
        plans.map { it.cadence }.distinctUntilChanged().collectLatest { cadence ->
            log("cadence ${cadence?.intervalMs}")
            if (cadence == null) return@collectLatest // paused/off: no GPS, no writes
            // Conflated, and old fixes dropped: after an offline spell the partner must not see a backlog of
            // stale points each stamped "just now" by the server.
            updates.updates(cadence).conflate().collect { fix ->
                if (clock.now() - fix.at > STALE_FIX_MS) return@collect
                log("fix (acc ${fix.accuracyM})") // never the coordinates
                location.write(pid, uid, fix.lat, fix.lng, fix.accuracyM)
                currentTrip?.let { trip ->
                    if (arrival.arrived(trip, fix)) {
                        // Its own job: writing "arrived" ends the trip, which changes the cadence and restarts this
                        // collector (collectLatest) — seen on the emulator cancelling the check-in mid-way.
                        sideEffects.launch {
                            trips.arrive(pid, uid)
                            if (trip.notifyPartner) events.checkIn(pid, uid, ARRIVED_KIND, trip.dest?.label)
                        }
                    }
                }
            }
        }
    }

    /**
     * One detector per trip (keyed by its start), at most one arrival per trip, and only after having been
     * away from the destination at least once — a trip "back to where I am" must not arrive on its first fix.
     */
    private class ArrivalTracker {
        private var tripKey: Long? = null
        private var detector = ArrivalDetector()
        private var leftOnce = false
        private var done = false

        fun arrived(trip: Trip, fix: LocationFix): Boolean {
            val dest = trip.dest ?: return false
            if (trip.startedAt != tripKey) { tripKey = trip.startedAt; detector = ArrivalDetector(); leftOnce = false; done = false }
            if (done) return false
            val accuracy = fix.accuracyM ?: Float.MAX_VALUE // unknown accuracy can't prove arrival
            val distance = distanceMeters(fix.lat, fix.lng, dest.lat, dest.lng)
            if (accuracy <= LEAVE_ACCURACY_M && distance > maxOf(LEAVE_RADIUS_M, accuracy.toDouble())) leftOnce = true
            val atDestination = detector.onFix(distance, accuracy)
            done = leftOnce && atDestination
            return done
        }
    }

    private fun log(message: String) = runCatching { android.util.Log.d("AshGoShare", message) }

    private fun ticker(): Flow<Unit> = flow {
        while (true) { emit(Unit); delay(tickMs) }
    }

    companion object {
        const val ARRIVED_KIND = "arrived"
        private const val STALE_FIX_MS = 2 * 60_000L
        private const val LEAVE_RADIUS_M = 300.0
        private const val LEAVE_ACCURACY_M = 200f
    }
}
