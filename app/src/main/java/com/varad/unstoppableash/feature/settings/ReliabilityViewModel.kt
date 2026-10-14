package com.varad.unstoppableash.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Re-reads Android's settings whenever the screen comes back (the person may have just fixed something). */
class ReliabilityViewModel(private val probe: () -> ReliabilityChecks, connected: Flow<Boolean>) : ViewModel() {
    private val checks = MutableStateFlow(probe())

    val items: StateFlow<List<ReliabilityItem>> = combine(checks, connected.onStart { emit(false) }) { c, online ->
        reliabilityItems(c.copy(connected = online))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), reliabilityItems(probe()))

    fun refresh() { checks.value = probe() }
}
