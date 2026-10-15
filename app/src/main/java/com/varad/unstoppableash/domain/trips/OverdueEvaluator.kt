package com.varad.unstoppableash.domain.trips

import com.varad.unstoppableash.domain.model.TripSnapshot
import com.varad.unstoppableash.domain.model.TripState

enum class OverduePhase { NOT_DUE, ASK_TRAVELLER, ALERT_PARTNER }

/**
 * "Ask me first": at the deadline the traveller is prompted; only after [GRACE_MS]
 * without an answer is the partner alerted. Both phones evaluate this independently.
 */
object OverdueEvaluator {
    const val GRACE_MS = 600_000L

    fun evaluate(trip: TripSnapshot?, now: Long): OverduePhase {
        if (trip == null) return OverduePhase.NOT_DUE
        if (trip.state != TripState.ACTIVE && trip.state != TripState.OVERDUE) return OverduePhase.NOT_DUE
        val deadline = trip.extendedTo ?: trip.expectBy ?: return OverduePhase.NOT_DUE
        return when {
            now < deadline -> OverduePhase.NOT_DUE
            now < deadline + GRACE_MS -> OverduePhase.ASK_TRAVELLER
            else -> OverduePhase.ALERT_PARTNER
        }
    }
}
