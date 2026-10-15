package com.varad.unstoppableash.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** Re-reads Android's settings whenever the screen comes back (the person may have just fixed something). */
class ReliabilityViewModel(
    private val probe: () -> ReliabilityChecks,
    connected: Flow<Boolean>,
    /** The background "stay connected" service: without it, nothing arrives with AshGo closed. */
    serviceRunning: Flow<Boolean>,
    /** Whether the background service should be running now (only during their trip or followed sharing). */
    needed: Flow<Boolean> = flowOf(true),
) : ViewModel() {
    private val checks = MutableStateFlow(probe())

    val items: StateFlow<List<ReliabilityItem>> = combine(checks, connected.onStart { emit(false) }, serviceRunning, needed.onStart { emit(true) }) { c, online, running, need ->
        reliabilityItems(c.copy(connected = online && (running || !need)))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), reliabilityItems(probe()))

    fun refresh() { checks.value = probe() }
}
