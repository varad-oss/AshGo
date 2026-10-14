package com.varad.unstoppableash.domain.location

import com.varad.unstoppableash.domain.model.SharingMode
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

enum class PauseChoice { ONE_HOUR, UNTIL_MORNING, UNTIL_I_RESUME }

/** Time-boxed pausing (J11). A pause with an end time ends by itself; the last point stays visible as paused (D-14). */
object SharingPlan {
    private val MORNING: LocalTime = LocalTime.of(7, 0)

    fun effective(mode: SharingMode, until: Long?, now: Long): SharingMode =
        if (mode == SharingMode.PAUSED && until != null && now >= until) SharingMode.LIVE else mode

    /** Null means "until I turn it back on". */
    fun pauseUntil(choice: PauseChoice, now: Long, zone: ZoneId): Long? = when (choice) {
        PauseChoice.ONE_HOUR -> now + 3_600_000
        PauseChoice.UNTIL_MORNING -> {
            val local = Instant.ofEpochMilli(now).atZone(zone)
            val today = local.toLocalDate().atTime(MORNING).atZone(zone)
            (if (local.isBefore(today)) today else today.plusDays(1)).toInstant().toEpochMilli()
        }
        PauseChoice.UNTIL_I_RESUME -> null
    }
}
