package com.varad.unstoppableash.domain.model

enum class SharingMode { LIVE, PAUSED, OFF }

/** Raw partner presence as stored; every field may be absent (legacy or partial data). */
data class PresenceSnapshot(
    val online: Boolean?,
    val lastSeen: Long?,
    val heartbeatAt: Long?,
    val battery: Int?,
    val charging: Boolean?,
    val sharing: SharingMode?,
    val locationAt: Long?,
    val accuracyM: Float?,
    /** When a pause ends by itself; null for "until I resume" or not paused. */
    val sharingUntil: Long? = null,
)

sealed interface PresenceState {
    data class Live(val ageMs: Long) : PresenceState
    data class Recent(val ageMs: Long) : PresenceState
    data class Stale(val ageMs: Long) : PresenceState
    data class Paused(val lastLocationAt: Long?) : PresenceState
    data class Offline(val since: Long) : PresenceState
    data object NotSharing : PresenceState
    data object Unknown : PresenceState
}
