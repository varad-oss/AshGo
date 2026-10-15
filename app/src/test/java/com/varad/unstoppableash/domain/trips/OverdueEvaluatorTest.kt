package com.varad.unstoppableash.domain.trips

import com.varad.unstoppableash.domain.model.TripSnapshot
import com.varad.unstoppableash.domain.model.TripState
import com.varad.unstoppableash.domain.time.Durations.MINUTE
import org.junit.Assert.assertEquals
import org.junit.Test

class OverdueEvaluatorTest {
    private val expectBy = 5_000_000L
    private fun active(expect: Long? = expectBy, extended: Long? = null, state: TripState = TripState.ACTIVE) =
        TripSnapshot(state = state, expectBy = expect, extendedTo = extended, endedAt = null)
    private fun eval(t: TripSnapshot?, now: Long) = OverdueEvaluator.evaluate(t, now)

    @Test fun noTripIsNotDue() = assertEquals(OverduePhase.NOT_DUE, eval(null, expectBy))
    @Test fun arrivedIsNotDue() = assertEquals(OverduePhase.NOT_DUE, eval(active(state = TripState.ARRIVED), expectBy + 60 * MINUTE))
    @Test fun endedIsNotDue() = assertEquals(OverduePhase.NOT_DUE, eval(active(state = TripState.ENDED), expectBy + 60 * MINUTE))
    @Test fun noDeadlineIsNotDue() = assertEquals(OverduePhase.NOT_DUE, eval(active(expect = null), expectBy + 60 * MINUTE))
    @Test fun beforeDeadline() = assertEquals(OverduePhase.NOT_DUE, eval(active(), expectBy - 1))
    @Test fun atDeadlineAsksTraveller() = assertEquals(OverduePhase.ASK_TRAVELLER, eval(active(), expectBy))
    @Test fun endOfGraceStillAsks() = assertEquals(OverduePhase.ASK_TRAVELLER, eval(active(), expectBy + 599_999))
    @Test fun afterGraceAlertsPartner() = assertEquals(OverduePhase.ALERT_PARTNER, eval(active(), expectBy + 600_000))
    @Test fun extensionOverridesDeadline() =
        assertEquals(OverduePhase.NOT_DUE, eval(active(extended = expectBy + 15 * MINUTE), expectBy + 10 * MINUTE))
    @Test fun overdueStatePastGraceAlerts() =
        assertEquals(OverduePhase.ALERT_PARTNER, eval(active(state = TripState.OVERDUE), expectBy + 11 * MINUTE))
}
