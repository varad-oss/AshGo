package com.varad.unstoppableash.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LastSeenFormatTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 10, 13, 21, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun sameDayShowsTimeOnly() = assertEquals(LastSeenFormat.TIME_ONLY, lastSeenFormat(at(13, 0), now, zone))
    @Test fun yesterdayShowsWeekday() = assertEquals(LastSeenFormat.WEEKDAY_TIME, lastSeenFormat(at(12, 23), now, zone))
    @Test fun sixDaysAgoShowsWeekday() = assertEquals(LastSeenFormat.WEEKDAY_TIME, lastSeenFormat(at(7, 12), now, zone))
    @Test fun aWeekOrMoreShowsDate() = assertEquals(LastSeenFormat.DATE_TIME, lastSeenFormat(at(6, 12), now, zone))
    @Test fun futureDayShowsDate() = assertEquals(LastSeenFormat.DATE_TIME, lastSeenFormat(at(14, 9), now, zone))
}
