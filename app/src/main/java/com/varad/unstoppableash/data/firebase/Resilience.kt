package com.varad.unstoppableash.data.firebase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.retryWhen
import kotlin.math.min

/**
 * Observing flows must never turn a cancelled listener into "nothing there", and must never stop for
 * good (review C1). On [RtdbException] they keep their last value, wait with exponential backoff
 * (1 s … 30 s) and re-subscribe. [onDenied] may emit a typed value first (e.g. "not a member").
 */
internal fun <T> Flow<T>.resilient(onDenied: suspend FlowCollector<T>.(RtdbException) -> Unit = {}): Flow<T> =
    retryWhen { cause, attempt ->
        if (cause !is RtdbException) return@retryWhen false
        onDenied(cause)
        delay(min(1_000L shl attempt.coerceAtMost(5).toInt(), 30_000L))
        true
    }

/**
 * A denied write whose effect is already present (a replayed queued write won, or a retry raced the
 * first attempt) is a success, not an error (review I2).
 */
internal suspend fun com.varad.unstoppableash.core.result.Outcome<Unit>.orIfTrue(
    alreadyDone: suspend () -> Boolean,
): com.varad.unstoppableash.core.result.Outcome<Unit> =
    if (this is com.varad.unstoppableash.core.result.Outcome.Failure &&
        reason == com.varad.unstoppableash.core.result.FailureReason.PERMISSION_DENIED && alreadyDone()
    ) com.varad.unstoppableash.core.result.Outcome.Success(Unit) else this
