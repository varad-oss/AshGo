package com.varad.unstoppableash.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.varad.unstoppableash.R
import com.varad.unstoppableash.domain.model.PresenceState
import com.varad.unstoppableash.ui.theme.Orbit

/** How a presence state looks and reads. Colour is never the only signal: icon + text always accompany it. */
enum class PillBorder { NONE, SOLID, DASHED }

data class PresenceVisual(
    val icon: ImageVector,
    val container: Color,
    val ink: Color,
    val border: PillBorder,
    val text: String,
    val description: String,
)

@Composable
fun presenceVisual(name: String, state: PresenceState): PresenceVisual {
    val c = Orbit.colors
    val (container, ink, border) = when (presenceTone(state)) {
        PresenceTone.GOOD -> Triple(c.goodBg, c.goodInk, PillBorder.NONE)
        PresenceTone.GOOD_OUTLINE -> Triple(c.surfaceWarm, c.goodInk, PillBorder.SOLID)
        PresenceTone.WARN -> Triple(c.warnBg, c.warnInk, PillBorder.NONE)
        PresenceTone.PAUSED -> Triple(c.lavender, c.text, PillBorder.NONE)
        PresenceTone.NEUTRAL -> Triple(c.surfaceWarm, c.textSecondary, PillBorder.NONE)
        PresenceTone.NEUTRAL_DASHED -> Triple(c.surfaceWarm, c.textSecondary, PillBorder.DASHED)
    }
    return when (state) {
        is PresenceState.Live -> PresenceVisual(
            Icons.Rounded.FiberManualRecord, container, ink, border,
            stringResource(R.string.presence_live, shortAgeAgo(state.ageMs)),
            stringResource(R.string.presence_cd_live, name, longAgeAgo(state.ageMs)),
        )
        is PresenceState.Recent -> PresenceVisual(
            Icons.Rounded.RadioButtonChecked, container, ink, border,
            stringResource(R.string.presence_recent, shortAgeAgo(state.ageMs)),
            stringResource(R.string.presence_cd_recent, name, longAgeAgo(state.ageMs)),
        )
        is PresenceState.Stale -> PresenceVisual(
            Icons.Rounded.Schedule, container, ink, border,
            stringResource(R.string.presence_stale, shortAgeAgo(state.ageMs)),
            stringResource(R.string.presence_cd_stale, name, longAgeAgo(state.ageMs)),
        )
        is PresenceState.Paused -> {
            val at = state.lastLocationAt?.let { lastSeenText(it) }
            PresenceVisual(
                Icons.Rounded.Pause, container, ink, border,
                if (at == null) stringResource(R.string.presence_paused) else stringResource(R.string.presence_paused_last_seen, at),
                if (at == null) stringResource(R.string.presence_cd_paused, name)
                else stringResource(R.string.presence_cd_paused_last_seen, name, at),
            )
        }
        is PresenceState.Offline -> {
            val since = lastSeenText(state.since)
            PresenceVisual(
                Icons.Rounded.CloudOff, container, ink, border,
                stringResource(R.string.presence_offline_since, since),
                stringResource(R.string.presence_cd_offline, name, since),
            )
        }
        PresenceState.NotSharing -> PresenceVisual(
            Icons.Rounded.LocationOff, container, ink, border,
            stringResource(R.string.presence_not_sharing),
            stringResource(R.string.presence_cd_not_sharing, name),
        )
        PresenceState.Unknown -> PresenceVisual(
            Icons.AutoMirrored.Rounded.HelpOutline, container, ink, border,
            stringResource(R.string.presence_unknown),
            stringResource(R.string.presence_cd_unknown, name),
        )
    }
}
