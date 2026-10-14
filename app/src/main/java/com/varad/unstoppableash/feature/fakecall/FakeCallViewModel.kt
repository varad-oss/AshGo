package com.varad.unstoppableash.feature.fakecall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varad.unstoppableash.domain.model.FakeCallVoice
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CallDelay(val ms: Long) { NOW(0), TEN_SECONDS(10_000), ONE_MINUTE(60_000) }

sealed interface CallPhase {
    data object Setup : CallPhase
    data class Waiting(val ringsAtUptime: Long) : CallPhase
    data object Ringing : CallPhase
    data class InCall(val startedAtUptime: Long) : CallPhase
}

/** Ringtone and the "voice on the line"; Android's is [AndroidFakeCallAudio]. */
interface FakeCallAudio {
    fun ring()
    /** Null plays the built-in recording. */
    fun play(voice: FakeCallVoice?)
    fun stop()
}

data class FakeCallUiState(
    val caller: String = "",
    val voices: List<FakeCallVoice> = emptyList(),
    /** Null is the built-in recording. */
    val selectedVoiceId: String? = null,
    val delay: CallDelay = CallDelay.TEN_SECONDS,
    val phase: CallPhase = CallPhase.Setup,
    val addingFailed: Boolean = false,
)

/** Fake call (REDESIGN_SPEC §Safety): a believable incoming call to step away from a situation. Nothing is uploaded. */
class FakeCallViewModel(
    private val store: FakeCallVoiceStore,
    private val audio: FakeCallAudio,
    private val uptime: () -> Long = { 0L },
) : ViewModel() {

    private val local = MutableStateFlow(FakeCallUiState())
    private var waiting: Job? = null

    val state: StateFlow<FakeCallUiState> = combine(local, store.voices) { l, voices ->
        l.copy(voices = voices, selectedVoiceId = l.selectedVoiceId?.takeIf { id -> voices.any { it.id == id } })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FakeCallUiState())

    fun setCaller(name: String) = local.update { it.copy(caller = name.take(40)) }
    fun selectVoice(id: String?) = local.update { it.copy(selectedVoiceId = id) }
    fun setDelay(d: CallDelay) = local.update { it.copy(delay = d) }

    fun addVoice(name: String, uri: String) = viewModelScope.launch {
        val ok = store.add(name, uri)
        local.update { it.copy(addingFailed = !ok) }
    }

    fun removeVoice(id: String) = viewModelScope.launch { store.remove(id) }

    fun schedule(d: CallDelay = local.value.delay) {
        waiting?.cancel()
        local.update { it.copy(phase = CallPhase.Waiting(uptime() + d.ms)) }
        waiting = viewModelScope.launch {
            delay(d.ms)
            local.update { it.copy(phase = CallPhase.Ringing) }
            audio.ring()
        }
    }

    fun cancel() {
        waiting?.cancel()
        local.update { it.copy(phase = CallPhase.Setup) }
    }

    fun decline() {
        if (local.value.phase != CallPhase.Ringing) return
        audio.stop()
        local.update { it.copy(phase = CallPhase.Setup) }
    }

    fun accept() {
        if (local.value.phase != CallPhase.Ringing) return
        audio.stop()
        val voice = state.value.selectedVoiceId?.let { id -> state.value.voices.firstOrNull { it.id == id } }
        audio.play(voice)
        local.update { it.copy(phase = CallPhase.InCall(uptime())) }
    }

    fun end() {
        audio.stop()
        local.update { it.copy(phase = CallPhase.Setup) }
    }

    override fun onCleared() = audio.stop()
}
