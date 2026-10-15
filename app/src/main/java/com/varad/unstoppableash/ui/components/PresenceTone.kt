package com.varad.unstoppableash.ui.components

import com.varad.unstoppableash.domain.model.PresenceState
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Colour family per presence state. Only Live gets the filled "good" colours (REDESIGN_SPEC §2). */
enum class PresenceTone { GOOD, GOOD_OUTLINE, WARN, PAUSED, NEUTRAL, NEUTRAL_DASHED }

fun presenceTone(state: PresenceState): PresenceTone = when (state) {
    is PresenceState.Live -> PresenceTone.GOOD
    is PresenceState.Recent -> PresenceTone.GOOD_OUTLINE
    is PresenceState.Stale, is PresenceState.Offline -> PresenceTone.WARN
    is PresenceState.Paused -> PresenceTone.PAUSED
    PresenceState.NotSharing -> PresenceTone.NEUTRAL_DASHED
    PresenceState.Unknown -> PresenceTone.NEUTRAL
}

/** How much of a past timestamp to show so an old "since" never reads as today. */
enum class LastSeenFormat { TIME_ONLY, WEEKDAY_TIME, DATE_TIME }

fun lastSeenFormat(epochMs: Long, nowMs: Long, zone: ZoneId): LastSeenFormat {
    val day = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(day, today)
    return when {
        daysAgo == 0L -> LastSeenFormat.TIME_ONLY
        daysAgo in 1..6 -> LastSeenFormat.WEEKDAY_TIME
        else -> LastSeenFormat.DATE_TIME
    }
}
