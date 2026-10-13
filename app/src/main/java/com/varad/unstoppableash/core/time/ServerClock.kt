package com.varad.unstoppableash.core.time

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Local wall clock corrected by Firebase's `.info/serverTimeOffset`, so freshness never depends on phone clocks. */
class ServerClock(
    offsets: Flow<Long>,
    scope: CoroutineScope,
    private val local: Clock = Clock { System.currentTimeMillis() },
) : Clock {
    @Volatile private var offset = 0L

    init {
        scope.launch { offsets.collect { offset = it } }
    }

    override fun now(): Long = local.now() + offset
}
