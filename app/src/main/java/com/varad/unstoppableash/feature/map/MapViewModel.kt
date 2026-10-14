package com.varad.unstoppableash.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.location.LocationRepository
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.presence.PresenceRepository
import com.varad.unstoppableash.data.trips.TripRepository
import com.varad.unstoppableash.domain.location.PauseChoice
import com.varad.unstoppableash.domain.location.SharingPlan
import com.varad.unstoppableash.domain.model.LocationFix
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.model.PresenceSnapshot
import com.varad.unstoppableash.domain.model.PresenceState
import com.varad.unstoppableash.domain.model.SharingMode
import com.varad.unstoppableash.domain.model.Trip
import com.varad.unstoppableash.domain.model.TripDestination
import com.varad.unstoppableash.domain.model.TripState
import com.varad.unstoppableash.domain.presence.PresenceCalculator
import com.varad.unstoppableash.domain.trips.OverdueEvaluator
import com.varad.unstoppableash.domain.trips.OverduePhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

data class MapUiState(
    val loading: Boolean = true,
    val myName: String = "",
    val partnerName: String = "",
    val partnerPresence: PresenceState = PresenceState.Unknown,
    /** Null when there is no location at all: the map then shows no partner pin, never a guess. */
    val partnerFix: LocationFix? = null,
    /** Shown faded: the pin is real but not current (Stale, Offline, Paused). */
    val partnerFaded: Boolean = false,
    val partnerBattery: Int? = null,
    val myFix: LocationFix? = null,
    val mySharing: SharingMode = SharingMode.OFF,
    val mySharingUntil: Long? = null,
    val myTrip: Trip? = null,
    val partnerTrip: Trip? = null,
    val myTripPhase: OverduePhase = OverduePhase.NOT_DUE,
    val partnerTripPhase: OverduePhase = OverduePhase.NOT_DUE,
    val nowMs: Long = 0L,
)

/** Map tab (REDESIGN_SPEC §Map, J6, J9): both pins as they really are, my sharing controls, and trips. */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(
    pairs: PairRepository,
    private val presence: PresenceRepository,
    private val location: LocationRepository,
    private val trips: TripRepository,
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    @Volatile private var current: PairState.Paired? = null

    private val paired: Flow<PairState.Paired?> = pairs.pairState().map { it as? PairState.Paired }
        .distinctUntilChanged { a, b -> a?.meta?.pairId == b?.meta?.pairId && a?.myUid == b?.myUid && a?.partner?.name == b?.partner?.name }

    val state: StateFlow<MapUiState> = paired.flatMapLatest { p ->
        current = p
        if (p == null) flowOf(MapUiState(loading = false)) else stateOf(p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), MapUiState())

    private fun stateOf(p: PairState.Paired): Flow<MapUiState> {
        val pid = p.meta.pairId
        return combine(
            listOf(
                presence.snapshot(pid, p.partnerUid).map<PresenceSnapshot, PresenceSnapshot?> { it }.onStart { emit(null) },
                location.observe(pid, p.partnerUid).onStart { emit(null) },
                presence.snapshot(pid, p.myUid).map<PresenceSnapshot, PresenceSnapshot?> { it }.onStart { emit(null) },
                location.observe(pid, p.myUid).onStart { emit(null) },
                trips.observe(pid, p.myUid).onStart { emit(null) },
                trips.observe(pid, p.partnerUid).onStart { emit(null) },
                ticker().map { clock.now() },
            ),
        ) { v ->
            val partner = v[0] as PresenceSnapshot?
            val me = v[2] as PresenceSnapshot?
            val now = v[6] as Long
            val partnerPresence = partner?.let { PresenceCalculator.compute(it, now) } ?: PresenceState.Unknown
            val myTrip = v[4] as Trip?
            val partnerTrip = v[5] as Trip?
            MapUiState(
                loading = partner == null,
                myName = p.me?.name.orEmpty(),
                partnerName = p.partner?.name.orEmpty(),
                partnerPresence = partnerPresence,
                partnerFix = v[1] as LocationFix?,
                partnerFaded = partnerPresence !is PresenceState.Live && partnerPresence !is PresenceState.Recent,
                partnerBattery = partner?.battery,
                myFix = v[3] as LocationFix?,
                mySharing = me?.let { SharingPlan.effective(it.sharing ?: SharingMode.OFF, it.sharingUntil, now) } ?: SharingMode.OFF,
                mySharingUntil = me?.sharingUntil,
                myTrip = myTrip?.takeIf { it.state == TripState.ACTIVE || it.state == TripState.OVERDUE },
                partnerTrip = partnerTrip?.takeIf { it.state == TripState.ACTIVE || it.state == TripState.OVERDUE },
                myTripPhase = OverdueEvaluator.evaluate(myTrip?.snapshot, now),
                partnerTripPhase = OverdueEvaluator.evaluate(partnerTrip?.snapshot, now),
                nowMs = now,
            )
        }
    }

    fun shareLive() = withPair { presence.setSharing(it.meta.pairId, it.myUid, SharingMode.LIVE, null) }

    fun pause(choice: PauseChoice) = withPair {
        presence.setSharing(it.meta.pairId, it.myUid, SharingMode.PAUSED, SharingPlan.pauseUntil(choice, clock.now(), zone))
    }

    fun stopSharing() = withPair { presence.setSharing(it.meta.pairId, it.myUid, SharingMode.OFF, null) }

    fun startTrip(dest: TripDestination?, expectBy: Long?, note: String?, notifyPartner: Boolean) = withPair {
        trips.start(it.meta.pairId, it.myUid, dest, expectBy, note, notifyPartner)
    }

    /** "+15 min": from the current deadline, or from now if that has already passed. */
    fun extendTrip() = withPair { p ->
        val trip = state.value.myTrip ?: return@withPair
        val deadline = trip.extendedTo ?: trip.expectBy ?: clock.now()
        trips.extend(p.meta.pairId, p.myUid, maxOf(deadline, clock.now()) + EXTEND_MS)
    }

    fun arrive() = withPair { trips.arrive(it.meta.pairId, it.myUid) }
    fun endTrip() = withPair { trips.end(it.meta.pairId, it.myUid) }

    private fun withPair(block: suspend (PairState.Paired) -> Unit) {
        val p = current ?: return
        viewModelScope.launch { block(p) }
    }

    private fun ticker(): Flow<Unit> = flow { while (true) { emit(Unit); delay(30_000) } }

    companion object {
        const val EXTEND_MS = 15 * 60_000L
    }
}
