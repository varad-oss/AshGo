package com.varad.unstoppableash.domain.presence

import com.varad.unstoppableash.domain.model.PresenceSnapshot
import com.varad.unstoppableash.domain.model.PresenceState
import com.varad.unstoppableash.domain.model.SharingMode

/**
 * The only place a partner's presence is interpreted. "Live" requires a real fix
 * younger than [LIVE_MS]; the user's own sharing choice outranks connectivity.
 */
object PresenceCalculator {
    const val LIVE_MS = 120_000L
    const val RECENT_MS = 900_000L
    const val OFFLINE_GRACE_MS = 120_000L
    /** Timestamps up to this far in the future are clock skew; anything further is not trusted. */
    const val FUTURE_TOLERANCE_MS = 120_000L

    fun compute(s: PresenceSnapshot?, now: Long): PresenceState {
        if (s == null) return PresenceState.Unknown
        when (s.sharing) {
            SharingMode.PAUSED -> return PresenceState.Paused(s.locationAt)
            SharingMode.OFF -> return PresenceState.NotSharing
            SharingMode.LIVE, null -> Unit
        }
        if (s.online == false && s.lastSeen != null && now - s.lastSeen > OFFLINE_GRACE_MS) {
            return PresenceState.Offline(s.lastSeen)
        }
        val locationAt = s.locationAt ?: return PresenceState.Unknown
        if (locationAt - now > FUTURE_TOLERANCE_MS) return PresenceState.Unknown
        val age = (now - locationAt).coerceAtLeast(0)
        return when {
            age < LIVE_MS -> PresenceState.Live(age)
            age < RECENT_MS -> PresenceState.Recent(age)
            else -> PresenceState.Stale(age)
        }
    }
}
