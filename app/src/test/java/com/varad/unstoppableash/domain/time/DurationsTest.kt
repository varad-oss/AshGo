package com.varad.unstoppableash.domain.time

import com.varad.unstoppableash.domain.time.Durations.DAY
import com.varad.unstoppableash.domain.time.Durations.HOUR
import com.varad.unstoppableash.domain.time.Durations.MINUTE
import org.junit.Assert.assertEquals
import org.junit.Test

class DurationsTest {
    @Test fun negativeAgeClampsToJustNow() = assertEquals(AgeText(0, AgeUnit.JUST_NOW), formatAge(-5_000))
    @Test fun underOneMinuteIsJustNow() = assertEquals(AgeText(0, AgeUnit.JUST_NOW), formatAge(59_999))
    @Test fun oneMinute() = assertEquals(AgeText(1, AgeUnit.MINUTES), formatAge(60_000))
    @Test fun minutesFloor() = assertEquals(AgeText(59, AgeUnit.MINUTES), formatAge(59 * MINUTE + 59_999))
    @Test fun oneHour() = assertEquals(AgeText(1, AgeUnit.HOURS), formatAge(HOUR))
    @Test fun hoursFloor() = assertEquals(AgeText(23, AgeUnit.HOURS), formatAge(23 * HOUR + 59 * MINUTE))
    @Test fun oneDay() = assertEquals(AgeText(1, AgeUnit.DAYS), formatAge(DAY))
}
