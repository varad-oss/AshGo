package com.varad.unstoppableash.feature.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.sos.SosRepository
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.model.SosRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PartnerSosUiState(
    val loading: Boolean = true,
    val partnerName: String = "",
    /** The partner's latest SOS, active or cancelled; never mine. */
    val record: SosRecord? = null,
    val acked: Boolean = false,
)

/**
 * The partner's SOS (REDESIGN_SPEC §PartnerSos). "Delivered" is written only while this screen is really
 * showing it, "acknowledged" only when the person taps Acknowledge — the sender sees exactly that, no more.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PartnerSosViewModel(pairs: PairRepository, private val sos: SosRepository) : ViewModel() {

    private val visible = MutableStateFlow(false)

    private val paired: Flow<PairState.Paired?> = pairs.pairState().map { it as? PairState.Paired }
        .distinctUntilChanged { a, b -> a?.meta?.pairId == b?.meta?.pairId && a?.myUid == b?.myUid && a?.partner?.name == b?.partner?.name }

    val state: StateFlow<PartnerSosUiState> = paired.flatMapLatest { p ->
        if (p == null) flowOf(PartnerSosUiState(loading = false))
        else sos.records(p.meta.pairId).map { list ->
            val r = list.lastOrNull { it.from != p.myUid }
            PartnerSosUiState(false, p.partner?.name.orEmpty(), r, acked = r != null && p.myUid in r.ackBy)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), PartnerSosUiState())

    @Volatile private var current: PairState.Paired? = null

    init {
        viewModelScope.launch { paired.collect { current = it } }
        viewModelScope.launch {
            combine(visible, state) { v, s -> if (v) s.record else null }.collect { r ->
                val p = current ?: return@collect
                // Idempotent in the repository: an existing receipt is never rewritten.
                if (r != null && r.state == com.varad.unstoppableash.domain.model.SosRecordState.ACTIVE) sos.markDelivered(p.meta.pairId, r, p.myUid)
            }
        }
    }

    fun setVisible(isVisible: Boolean) { visible.value = isVisible }

    fun acknowledge() {
        val p = current ?: return
        val r = state.value.record ?: return
        viewModelScope.launch { sos.ack(p.meta.pairId, r, p.myUid) }
    }
}
