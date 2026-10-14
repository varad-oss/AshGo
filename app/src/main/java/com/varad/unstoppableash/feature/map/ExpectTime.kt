package com.varad.unstoppableash.feature.map

import java.time.Instant
import java.time.ZoneId

/** "Expect me back by" a clock time: the next time it comes round (later today, else tomorrow). */
object ExpectTime {
    fun next(hour: Int, minute: Int, now: Long, zone: ZoneId): Long {
        val today = Instant.ofEpochMilli(now).atZone(zone).withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        val at = if (today.toInstant().toEpochMilli() > now) today else today.plusDays(1)
        return at.toInstant().toEpochMilli()
    }
}
