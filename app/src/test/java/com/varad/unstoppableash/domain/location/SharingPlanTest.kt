package com.varad.unstoppableash.domain.location

import com.varad.unstoppableash.domain.model.SharingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SharingPlanTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(day: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun aTimedPauseEndsByItself() {
        val until = at(14, 21)
        assertEquals(SharingMode.PAUSED, SharingPlan.effective(SharingMode.PAUSED, until, at(14, 20, 59)))
        assertEquals(SharingMode.LIVE, SharingPlan.effective(SharingMode.PAUSED, until, until))
    }

    @Test fun anOpenEndedPauseLastsUntilResumed() {
        assertEquals(SharingMode.PAUSED, SharingPlan.effective(SharingMode.PAUSED, null, Long.MAX_VALUE))
    }

    @Test fun liveAndOffIgnoreTheTimer() {
        assertEquals(SharingMode.OFF, SharingPlan.effective(SharingMode.OFF, at(1, 1), at(30, 1)))
        assertEquals(SharingMode.LIVE, SharingPlan.effective(SharingMode.LIVE, at(1, 1), at(30, 1)))
    }

    @Test fun pauseChoices() {
        val now = at(14, 22, 30)
        assertEquals(now + 3_600_000, SharingPlan.pauseUntil(PauseChoice.ONE_HOUR, now, zone))
        assertEquals("next 07:00 local", at(15, 7), SharingPlan.pauseUntil(PauseChoice.UNTIL_MORNING, now, zone))
        assertEquals("before 07:00 it is this morning", at(14, 7), SharingPlan.pauseUntil(PauseChoice.UNTIL_MORNING, at(14, 3), zone))
        assertNull(SharingPlan.pauseUntil(PauseChoice.UNTIL_I_RESUME, now, zone))
    }

    @Test fun thisPhoneSharesOnlyWhenChosenHere() {
        assertTrue(SharingPlan.runHere(paired = true, mode = SharingMode.LIVE, until = null, chosenHere = true))
        assertTrue("a timed pause keeps the service so it can resume", SharingPlan.runHere(true, SharingMode.PAUSED, until = 5L, chosenHere = true))
        assertFalse("an open-ended pause needs no service", SharingPlan.runHere(true, SharingMode.PAUSED, until = null, chosenHere = true))
        assertFalse(SharingPlan.runHere(true, SharingMode.OFF, null, true))
        assertFalse("never without an orbit", SharingPlan.runHere(false, SharingMode.LIVE, null, true))
        assertFalse("sharing on for the account elsewhere doesn't start this phone", SharingPlan.runHere(true, SharingMode.LIVE, null, chosenHere = false))
    }
}
