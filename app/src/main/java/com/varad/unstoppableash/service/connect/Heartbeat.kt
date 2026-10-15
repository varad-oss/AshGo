package com.varad.unstoppableash.service.connect

import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.presence.PresenceRepository
import com.varad.unstoppableash.domain.model.PairState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One heartbeat (ARCHITECTURE §8): proves this phone is alive even when nothing else is written, so the partner's
 * "last update" stays honest. False when there is no orbit or no answer in time — the next run tries again.
 */
suspend fun heartbeatOnce(pairs: PairRepository, presence: PresenceRepository, timeoutMs: Long = 20_000): Boolean {
    val p = withTimeoutOrNull(timeoutMs) { pairs.pairState().first() } as? PairState.Paired ?: return false
    return withTimeoutOrNull(timeoutMs) { presence.heartbeat(p.meta.pairId, p.myUid) } is Outcome.Success
}
