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
import kotlinx.coroutines.Job
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

/** Emits now and then every [periodMs], so things that age on screen keep ageing without new data. */
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

    /** Tagged with the pair it happened in, so nothing follows you into another orbit or account. */
    private data class Tagged<T>(val pairId: String?, val value: T)

    private val action = MutableStateFlow(Tagged<ActionStatus>(null, ActionStatus.Idle))
    private val error = MutableStateFlow<Tagged<FailureReason>?>(null)
    @Volatile private var current: PairState.Paired? = null
    private var clearSaved: Job? = null

    init {
        viewModelScope.launch { rtdb.readFailures.collect { error.value = Tagged(current?.meta?.pairId, it) } }
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
        if (paired == null) flowOf(HomeUiState(loading = false, unpaired = true)) else detailsOf(paired)
    }

    val state: StateFlow<HomeUiState> =
        combine(details, action, error, rtdb.connected().onStart { emit(true) }) { d, a, e, connected ->
            val pairId = current?.meta?.pairId
            d.copy(
                action = a.value.takeIf { a.pairId == pairId } ?: ActionStatus.Idle,
                error = e?.value?.takeIf { e.pairId == pairId },
                connectionProblem = if (connected) null else FailureReason.OFFLINE,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), HomeUiState())

    /**
     * Only the partner's presence gates "loading"; every other stream starts empty and fills in, so
     * one slow or denied path can never hide the rest — above all, never a partner SOS.
     */
    private fun detailsOf(p: PairState.Paired): Flow<HomeUiState> {
        val pid = p.meta.pairId
        val partnerSnapshot = presence.snapshot(pid, p.partnerUid)
        return combine(
            listOf(
                partnerSnapshot.map<PresenceSnapshot, PresenceSnapshot?> { it }.onStart { emit(null) },
                location.observe(pid, p.partnerUid).onStart { emit(null) },
                presence.snapshot(pid, p.myUid).map { it.sharing ?: SharingMode.OFF }.onStart { emit(SharingMode.OFF) },
                chat.unreadCount(pid, p.myUid).onStart { emit(0) },
                events.events(pid).map { list -> list.lastOrNull { it.from == p.partnerUid && it.type == EventType.CHECKIN } }.onStart { emit(null) },
                sos.partnerActiveSos(pid, p.myUid).onStart { emit(null) },
                trips.observe(pid, p.partnerUid).onStart { emit(null) },
                ticker(NOW_TICK_MS).map { clock.now() }, // re-ages presence and "x min ago" without new data
            ),
        ) { v ->
            val snapshot = v[0] as PresenceSnapshot?
            val now = v[7] as Long
            HomeUiState(
                loading = snapshot == null,
                myName = p.me?.name.orEmpty(),
                myAvatar = p.me?.avatar,
                partnerName = p.partner?.name.orEmpty(),
                partnerAvatar = p.partner?.avatar,
                relationship = p.meta.relationship,
                greetingEnabled = p.me?.greeting ?: true,
                partnerPresence = snapshot?.let { PresenceCalculator.compute(it, now) } ?: PresenceState.Unknown,
                partnerBattery = snapshot?.battery,
                partnerCharging = snapshot?.charging,
                partnerLocation = v[1] as LocationFix?,
                mySharing = v[2] as SharingMode,
                unread = v[3] as Int,
                latestPartnerCheckIn = v[4] as OrbitEvent?,
                partnerSos = v[5] as SosRecord?,
                partnerTrip = v[6] as Trip?,
                nowMs = now,
            )
        }
    }

    fun checkIn(kind: String, text: String?) = run(Action.CHECK_IN) { p -> events.checkIn(p.meta.pairId, p.myUid, kind, text) }

    fun nudge(kind: String) = run(Action.NUDGE) { p -> events.nudge(p.meta.pairId, p.myUid, kind) }

    companion object {
        /** "Saved ✓" is a receipt, not a state: it fades so it never describes an old action. */
        const val SAVED_VISIBLE_MS = 4_000L
        private const val NOW_TICK_MS = 30_000L
    }

    fun dismissError() { error.value = null }

    private fun run(kind: Action, block: suspend (PairState.Paired) -> Outcome<*>) {
        val paired = current ?: return
        val pid = paired.meta.pairId
        viewModelScope.launch {
            clearSaved?.cancel() // only the fade timer: an earlier write in flight still finishes
            action.value = Tagged(pid, ActionStatus.Sending(kind))
            when (val r = block(paired)) {
                is Outcome.Success -> {
                    val saved = Tagged<ActionStatus>(pid, ActionStatus.Saved(kind))
                    action.value = saved
                    clearSaved = viewModelScope.launch {
                        delay(SAVED_VISIBLE_MS)
                        if (action.value == saved) action.value = Tagged(pid, ActionStatus.Idle)
                    }
                }
                is Outcome.Failure -> action.value = Tagged(pid, ActionStatus.Failed(kind, r.reason))
            }
        }
    }
}
