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
            .collectLatest { p -> if (p != null) runFor(p.meta.pairId, p.myUid) }
    }

    private suspend fun runFor(pid: String, uid: String) = coroutineScope {
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
            val active = trip?.takeIf { it.state == TripState.ACTIVE }
            Plan(CadencePolicy.cadenceFor(effective, active != null, batt?.pct, batt?.charging), active)
        }.distinctUntilChanged().shareIn(this, SharingStarted.Eagerly, replay = 1) // one set of listeners for both uses

        var currentTrip: Trip? = null
        val arrival = ArrivalTracker()
        launch { plans.collect { currentTrip = it.trip } }
        plans.map { it.cadence }.distinctUntilChanged().collectLatest { cadence ->
            if (cadence == null) return@collectLatest // paused/off: no GPS, no writes
            updates.updates(cadence).collect { fix ->
                location.write(pid, uid, fix.lat, fix.lng, fix.accuracyM)
                currentTrip?.let { trip ->
                    if (arrival.arrived(trip, fix)) {
                        trips.arrive(pid, uid)
                        if (trip.notifyPartner) events.checkIn(pid, uid, ARRIVED_KIND, trip.dest?.label)
                    }
                }
            }
        }
    }

    /** One detector per trip (keyed by its start), and at most one arrival per trip. */
    private class ArrivalTracker {
        private var tripKey: Long? = null
        private var detector = ArrivalDetector()
        private var done = false

        fun arrived(trip: Trip, fix: LocationFix): Boolean {
            val dest = trip.dest ?: return false
            if (trip.startedAt != tripKey) { tripKey = trip.startedAt; detector = ArrivalDetector(); done = false }
            if (done) return false
            val accuracy = fix.accuracyM ?: Float.MAX_VALUE // unknown accuracy can't prove arrival
            done = detector.onFix(distanceMeters(fix.lat, fix.lng, dest.lat, dest.lng), accuracy)
            return done
        }
    }

    private fun ticker(): Flow<Unit> = flow {
        while (true) { emit(Unit); delay(tickMs) }
    }

    companion object {
        const val ARRIVED_KIND = "arrived"
    }
}
