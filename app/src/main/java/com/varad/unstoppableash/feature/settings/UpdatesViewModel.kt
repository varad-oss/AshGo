package com.varad.unstoppableash.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.data.update.UpdateCheck
import com.varad.unstoppableash.data.update.UpdateFailure
import com.varad.unstoppableash.data.update.UpdateInfo
import com.varad.unstoppableash.data.update.UpdateSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdatesState {
    data object Idle : UpdatesState
    data object Checking : UpdatesState
    data object UpToDate : UpdatesState
    data object CannotCheck : UpdatesState
    data class Found(val info: UpdateInfo) : UpdatesState
    data class Downloading(val info: UpdateInfo, val progress: Float?) : UpdatesState
    data object Installing : UpdatesState
    data class Failed(val reason: UpdateFailure) : UpdatesState
}

/** Settings → Updates (REDESIGN_SPEC): every step and every failure is shown; nothing installs unverified. */
class UpdatesViewModel(private val source: UpdateSource) : ViewModel() {
    private val _state = MutableStateFlow<UpdatesState>(UpdatesState.Idle)
    val state: StateFlow<UpdatesState> = _state.asStateFlow()

    fun check() {
        if (_state.value is UpdatesState.Checking || _state.value is UpdatesState.Downloading) return
        _state.value = UpdatesState.Checking
        viewModelScope.launch {
            _state.value = when (val r = source.check()) {
                is UpdateCheck.Available -> UpdatesState.Found(r.info)
                UpdateCheck.UpToDate -> UpdatesState.UpToDate
                UpdateCheck.Unavailable -> UpdatesState.CannotCheck
            }
        }
    }

    fun download() {
        val info = (_state.value as? UpdatesState.Found)?.info ?: return
        _state.value = UpdatesState.Downloading(info, null)
        viewModelScope.launch {
            when (val r = source.download(info) { p -> _state.value = UpdatesState.Downloading(info, p) }) {
                is Outcome.Success -> { _state.value = UpdatesState.Installing; source.install(r.value) }
                is Outcome.Failure -> _state.value = UpdatesState.Failed(runCatching { UpdateFailure.valueOf(r.detail.orEmpty()) }.getOrDefault(UpdateFailure.NETWORK))
            }
        }
    }
}
