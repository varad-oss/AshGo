package com.varad.unstoppableash.feature.map

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ExpectTimeTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(h: Int, m: Int, day: Int = 15) = LocalDateTime.of(2026, 10, day, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test fun aLaterTimeTodayIsToday() = assertEquals(at(21, 30), ExpectTime.next(21, 30, at(18, 0), zone))

    @Test fun aTimeAlreadyPastMeansTomorrow() = assertEquals(at(7, 0, day = 16), ExpectTime.next(7, 0, at(22, 15), zone))
}
