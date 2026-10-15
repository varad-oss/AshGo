package com.varad.unstoppableash.domain.time

object Durations {
    const val MINUTE = 60_000L
    const val HOUR = 3_600_000L
    const val DAY = 86_400_000L
}

enum class AgeUnit { JUST_NOW, MINUTES, HOURS, DAYS }

data class AgeText(val value: Int, val unit: AgeUnit)

/** Floors an age into its largest whole unit; negative ages (clock skew) read as "just now". */
fun formatAge(ageMs: Long): AgeText {
    val age = ageMs.coerceAtLeast(0)
    return when {
        age < Durations.MINUTE -> AgeText(0, AgeUnit.JUST_NOW)
        age < Durations.HOUR -> AgeText((age / Durations.MINUTE).toInt(), AgeUnit.MINUTES)
        age < Durations.DAY -> AgeText((age / Durations.HOUR).toInt(), AgeUnit.HOURS)
        else -> AgeText((age / Durations.DAY).toInt(), AgeUnit.DAYS)
    }
}
