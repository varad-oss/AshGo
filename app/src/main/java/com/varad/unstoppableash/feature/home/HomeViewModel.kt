package com.varad.unstoppableash.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.chat.ChatRepository
import com.varad.unstoppableash.data.events.EventRepository
import com.varad.unstoppableash.data.firebase.RtdbClient
import com.varad.unstoppableash.data.location.LocationRepository
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.presence.PresenceRepository
import com.varad.unstoppableash.data.sos.SosRepository
import com.varad.unstoppableash.data.trips.TripRepository
import com.varad.unstoppableash.domain.model.EventType
import com.varad.unstoppableash.domain.model.LocationFix
import com.varad.unstoppableash.domain.model.OrbitEvent
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.model.PresenceSnapshot
import com.varad.unstoppableash.domain.model.PresenceState
import com.varad.unstoppableash.domain.model.SharingMode
import com.varad.unstoppableash.domain.model.SosRecord
import com.varad.unstoppableash.domain.model.Trip
import com.varad.unstoppableash.domain.presence.PresenceCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Re-evaluates presence on a timer as well as on new data, so "Live" ages into "Recent" / "Stale"
 * on screen even when the partner's phone goes quiet.
 */
fun presenceStates(snapshots: Flow<PresenceSnapshot>, clock: Clock, tickMs: Long = 30_000): Flow<PresenceState> =
    combine(snapshots, ticker(tickMs)) { snapshot, _ -> PresenceCalculator.compute(snapshot, clock.now()) }.distinctUntilChanged()

private fun ticker(periodMs: Long): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(periodMs)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val pairs: PairRepository,
    private val presence: PresenceRepository,
    private val location: LocationRepository,
    private val chat: ChatRepository,
    private val events: EventRepository,
    private val sos: SosRepository,
    private val trips: TripRepository,
    private val rtdb: RtdbClient,
    private val clock: Clock,
) : ViewModel() {

    private val action = MutableStateFlow<ActionStatus>(ActionStatus.Idle)
    private val error = MutableStateFlow<FailureReason?>(null)
    @Volatile private var current: PairState.Paired? = null

    init {
        viewModelScope.launch { rtdb.readFailures.collect { error.value = it } }
        // While Home is alive, mark me online. onDisconnect handlers are consumed when the connection
        // drops, so re-register on every reconnect.
        viewModelScope.launch {
            pairs.pairState().map { it as? PairState.Paired }
                .distinctUntilChanged { a, b -> a?.meta?.pairId == b?.meta?.pairId && a?.myUid == b?.myUid }
                .collectLatest { paired ->
                    if (paired != null) rtdb.connected().filter { it }.collect { presence.goOnline(paired.meta.pairId, paired.myUid) }
                }
        }
    }

    private val details: Flow<HomeUiState> = pairs.pairState().flatMapLatest { state ->
        val paired = state as? PairState.Paired
        current = paired
        if (paired == null) flowOf(HomeUiState(loading = state == null, unpaired = true)) else detailsOf(paired)
    }

    val state: StateFlow<HomeUiState> =
        combine(details, action, error, rtdb.connected().onStart { emit(true) }) { d, a, e, connected ->
            d.copy(action = a, error = e, connectionProblem = if (connected) null else FailureReason.OFFLINE)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun detailsOf(p: PairState.Paired): Flow<HomeUiState> {
        val pid = p.meta.pairId
        val partnerSnapshot = presence.snapshot(pid, p.partnerUid)
        return combine(
            listOf(
                presenceStates(partnerSnapshot, clock),
                partnerSnapshot,
                location.observe(pid, p.partnerUid),
                presence.snapshot(pid, p.myUid).map { it.sharing ?: SharingMode.OFF },
                chat.unreadCount(pid, p.myUid),
                events.events(pid).map { list -> list.lastOrNull { it.from == p.partnerUid && it.type == EventType.CHECKIN } },
                sos.partnerActiveSos(pid, p.myUid),
                trips.observe(pid, p.partnerUid),
            ),
        ) { v ->
            val snapshot = v[1] as PresenceSnapshot
            HomeUiState(
                loading = false,
                myName = p.me?.name.orEmpty(),
                myAvatar = p.me?.avatar,
                partnerName = p.partner?.name.orEmpty(),
                partnerAvatar = p.partner?.avatar,
                relationship = p.meta.relationship,
                greetingEnabled = p.me?.greeting ?: true,
                partnerPresence = v[0] as PresenceState,
                partnerBattery = snapshot.battery,
                partnerCharging = snapshot.charging,
                partnerLocation = v[2] as LocationFix?,
                mySharing = v[3] as SharingMode,
                unread = v[4] as Int,
                latestPartnerCheckIn = v[5] as OrbitEvent?,
                partnerSos = v[6] as SosRecord?,
                partnerTrip = v[7] as Trip?,
            )
        }
    }

    fun checkIn(kind: String, text: String?) = run(Action.CHECK_IN) { p -> events.checkIn(p.meta.pairId, p.myUid, kind, text) }

    fun nudge(kind: String) = run(Action.NUDGE) { p -> events.nudge(p.meta.pairId, p.myUid, kind) }

    fun dismissError() { error.value = null }

    fun clearAction() { action.value = ActionStatus.Idle }

    private fun run(kind: Action, block: suspend (PairState.Paired) -> Outcome<*>) {
        val paired = current ?: return
        viewModelScope.launch {
            action.value = ActionStatus.Sending(kind)
            action.value = when (val r = block(paired)) {
                is Outcome.Success -> ActionStatus.Saved(kind)
                is Outcome.Failure -> ActionStatus.Failed(kind, r.reason)
            }
        }
    }
}
