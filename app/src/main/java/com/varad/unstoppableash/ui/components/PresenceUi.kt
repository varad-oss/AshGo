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
data class PresenceVisual(
    val icon: ImageVector,
    val container: Color,
    val ink: Color,
    val dashed: Boolean,
    val text: String,
    val description: String,
)

@Composable
fun presenceVisual(name: String, state: PresenceState): PresenceVisual {
    val c = Orbit.colors
    return when (state) {
        is PresenceState.Live -> PresenceVisual(
            Icons.Rounded.FiberManualRecord, c.goodBg, c.goodInk, false,
            stringResource(R.string.presence_live, shortAgeAgo(state.ageMs)),
            stringResource(R.string.presence_cd_live, name, longAgeAgo(state.ageMs)),
        )
        is PresenceState.Recent -> PresenceVisual(
            Icons.Rounded.RadioButtonChecked, c.goodBg, c.goodInk, false,
            stringResource(R.string.presence_recent, shortAgeAgo(state.ageMs)),
            stringResource(R.string.presence_cd_recent, name, longAgeAgo(state.ageMs)),
        )
        is PresenceState.Stale -> PresenceVisual(
            Icons.Rounded.Schedule, c.warnBg, c.warnInk, false,
            stringResource(R.string.presence_stale, shortAgeAgo(state.ageMs)),
            stringResource(R.string.presence_cd_stale, name, longAgeAgo(state.ageMs)),
        )
        is PresenceState.Paused -> {
            val at = state.lastLocationAt?.let { timeOfDay(it) }
            PresenceVisual(
                Icons.Rounded.Pause, c.lavender, c.text, false,
                if (at == null) stringResource(R.string.presence_paused) else stringResource(R.string.presence_paused_last_seen, at),
                if (at == null) stringResource(R.string.presence_cd_paused, name)
                else stringResource(R.string.presence_cd_paused_last_seen, name, at),
            )
        }
        is PresenceState.Offline -> {
            val since = timeOfDay(state.since)
            PresenceVisual(
                Icons.Rounded.CloudOff, c.warnBg, c.warnInk, false,
                stringResource(R.string.presence_offline_since, since),
                stringResource(R.string.presence_cd_offline, name, since),
            )
        }
        PresenceState.NotSharing -> PresenceVisual(
            Icons.Rounded.LocationOff, c.surfaceWarm, c.textSecondary, true,
            stringResource(R.string.presence_not_sharing),
            stringResource(R.string.presence_cd_not_sharing, name),
        )
        PresenceState.Unknown -> PresenceVisual(
            Icons.AutoMirrored.Rounded.HelpOutline, c.surfaceWarm, c.textSecondary, false,
            stringResource(R.string.presence_unknown),
            stringResource(R.string.presence_cd_unknown, name),
        )
    }
}
