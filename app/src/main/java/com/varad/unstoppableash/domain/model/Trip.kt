package com.varad.unstoppableash.domain.model

enum class TripState { ACTIVE, ARRIVED, ENDED, OVERDUE }

data class TripSnapshot(val state: TripState, val expectBy: Long?, val extendedTo: Long?, val endedAt: Long?)

data class TripDestination(val lat: Double, val lng: Double, val label: String?)

/** A member's trip as stored; [snapshot] is what OverdueEvaluator needs. */
data class Trip(
    val state: TripState,
    val dest: TripDestination?,
    val expectBy: Long?,
    val extendedTo: Long?,
    val note: String?,
    val notifyPartner: Boolean,
    val startedAt: Long?,
    val endedAt: Long?,
) {
    val snapshot: TripSnapshot get() = TripSnapshot(state, expectBy, extendedTo, endedAt)
}
