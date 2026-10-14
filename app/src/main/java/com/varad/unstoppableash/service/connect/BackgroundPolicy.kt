package com.varad.unstoppableash.service.connect

import com.varad.unstoppableash.domain.model.SharingMode
import com.varad.unstoppableash.domain.model.Trip
import com.varad.unstoppableash.domain.model.TripState

data class BackgroundInputs(
    val watcher: Boolean,
    val partnerTrip: Trip?,
    /** The other person's effective sharing (pauses already resolved). */
    val partnerSharing: SharingMode,
    /** Watcher's choice: keep following their live location with AshGo cleared. */
    val followSharing: Boolean,
    val myTrip: Trip?,
    /** Location sharing's own foreground service is running on this phone (it carries alerts too). */
    val locationServiceRunning: Boolean,
)

/**
 * When AshGo keeps running after it's cleared from recents (owner's ask): only while there's something to watch.
 * Otherwise clearing it really stops it. On screen, alerts work regardless.
 */
object BackgroundPolicy {
    fun keepConnected(i: BackgroundInputs): Boolean =
        if (i.watcher) i.partnerTrip.isOngoing() || (i.followSharing && i.partnerSharing == SharingMode.LIVE)
        else i.myTrip.isOngoing() && !i.locationServiceRunning

    private fun Trip?.isOngoing() = this != null && (state == TripState.ACTIVE || state == TripState.OVERDUE)
}
