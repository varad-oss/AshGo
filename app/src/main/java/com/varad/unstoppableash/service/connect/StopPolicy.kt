package com.varad.unstoppableash.service.connect

import com.varad.unstoppableash.domain.model.PairState

/**
 * When the "stay connected" service should stop itself. Signed out: now. Not paired: only if that lasts, because a
 * listener cancelled during a token refresh briefly reads as "unpaired", and a stopped foreground service can't be
 * restarted from the background on Android 12+ (partner alerts would stop until the app is opened).
 */
object StopPolicy {
    const val UNPAIRED_GRACE_MS = 60_000L
    /** Long enough for the notification engine to take this orbit's alerts down first. */
    const val SIGNED_OUT_STOP_MS = 2_000L

    /** Null: keep running. */
    fun stopAfterMs(state: PairState): Long? = when (state) {
        PairState.NotSignedIn -> SIGNED_OUT_STOP_MS
        is PairState.Paired -> null
        else -> UNPAIRED_GRACE_MS
    }
}
