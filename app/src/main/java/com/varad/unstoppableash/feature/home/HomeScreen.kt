package com.varad.unstoppableash.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.domain.copy.CopyVariant
import com.varad.unstoppableash.domain.copy.GreetingSlot
import com.varad.unstoppableash.domain.copy.copyVariantFor
import com.varad.unstoppableash.domain.copy.greetingSlot
import com.varad.unstoppableash.domain.model.PresenceState
import com.varad.unstoppableash.domain.model.SharingMode
import com.varad.unstoppableash.domain.model.TripState
import com.varad.unstoppableash.feature.map.PartnerOverdueCard
import com.varad.unstoppableash.ui.components.AvatarImage
import com.varad.unstoppableash.ui.components.ErrorBanner
import com.varad.unstoppableash.ui.components.LoadingOrbit
import com.varad.unstoppableash.ui.components.LoadingOrbitSize
import com.varad.unstoppableash.ui.components.OfflineBar
import com.varad.unstoppableash.ui.components.OrbitButton
import com.varad.unstoppableash.ui.components.OrbitButtonStyle
import com.varad.unstoppableash.ui.components.OrbitCard
import com.varad.unstoppableash.ui.components.PresenceRing
import com.varad.unstoppableash.ui.components.StatusPill
import com.varad.unstoppableash.ui.components.failureMessage
import com.varad.unstoppableash.ui.components.lastSeenText
import com.varad.unstoppableash.ui.components.shortAgeAgo
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.Spacing
import java.time.LocalTime

data class HomeActions(
    val onOpenSettings: () -> Unit = {},
    val onOpenMap: () -> Unit = {},
    val onOpenSafety: () -> Unit = {},
    val onOpenPartnerSos: () -> Unit = {},
    val onCheckIn: (kind: String, text: String?) -> Unit = { _, _ -> },
    val onNudge: (kind: String) -> Unit = {},
    val onDismissError: () -> Unit = {},
)

/** USER_JOURNEYS J5: everything important without scrolling past a wall of cards. */
@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    var sheet by remember { mutableStateOf<HomeSheet?>(null) }
    Column(modifier.fillMaxSize().background(Orbit.colors.background)) {
        if (state.connectionProblem != null) OfflineBar()
        // Safety first: a partner SOS and a read error show even before everything else has loaded.
        if (state.partnerSos != null || state.error != null) {
            Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.partnerSos?.let {
                    Surface(
                        color = Orbit.colors.sos, contentColor = Orbit.colors.onSos, shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = actions.onOpenPartnerSos),
                    ) {
                        Text(stringResource(R.string.partner_sos_banner, state.partnerName), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(Spacing.lg))
                    }
                }
                state.error?.let { ErrorBanner(failureMessage(it), onRetry = actions.onDismissError, actionLabel = stringResource(R.string.action_dismiss)) }
            }
        }
        if (state.loading || state.unpaired) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingOrbit(size = LoadingOrbitSize.LARGE) }
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(greeting(state), style = MaterialTheme.typography.titleLarge, color = Orbit.colors.text, modifier = Modifier.weight(1f))
                val settings = stringResource(R.string.open_settings)
                Box(Modifier.clip(MaterialTheme.shapes.extraLarge).clearAndSetSemantics { contentDescription = settings; role = Role.Button; onClick { actions.onOpenSettings(); true } }.clickable(onClick = actions.onOpenSettings)) {
                    AvatarImage(state.myName, state.myAvatar, size = 48.dp)
                }
            }

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PresenceRing(state.myName, state.myAvatar, state.partnerName, state.partnerAvatar, state.partnerPresence)
                Text(state.partnerName, style = MaterialTheme.typography.headlineSmall, color = Orbit.colors.text)
                StatusPill(state.partnerName, state.partnerPresence)
                battery(state)?.let { (text, low) ->
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = if (low) Orbit.colors.warnInk else Orbit.colors.textSecondary)
                }
            }

            state.partnerOverdueMs?.let { overdue ->
                PartnerOverdueCard(state.partnerName, overdue, state.partnerLocation, state.nowMs, onGetHelp = if (state.watcher) actions.onOpenMap else actions.onOpenSafety)
            }

            val trip = state.partnerTrip
            if (trip != null && trip.state == TripState.ACTIVE) {
                OrbitCard(Modifier.fillMaxWidth(), onClick = actions.onOpenMap) {
                    val deadline = trip.extendedTo ?: trip.expectBy
                    Text(
                        if (deadline != null) stringResource(R.string.trip_card_expected, lastSeenText(deadline)) else stringResource(R.string.trip_card_active),
                        style = MaterialTheme.typography.titleMedium, color = Orbit.colors.text,
                    )
                }
            } else {
                val fix = state.partnerLocation
                if (fix != null && state.partnerPresence !is PresenceState.NotSharing) {
                    OrbitCard(Modifier.fillMaxWidth(), onClick = actions.onOpenMap) {
                        Text(stringResource(R.string.location_card, shortAgeAgo(state.nowMs - fix.at)), style = MaterialTheme.typography.bodyLarge, color = Orbit.colors.text)
                    }
                }
            }

            state.latestPartnerCheckIn?.let { e ->
                Text(
                    stringResource(R.string.checkin_latest, state.partnerName, kindLabel(e.kind, e.text), lastSeenText(e.at)),
                    style = MaterialTheme.typography.bodyMedium, color = Orbit.colors.textSecondary,
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (!state.watcher) OrbitButton(stringResource(R.string.action_check_in), { sheet = HomeSheet.CHECK_IN })
                OrbitButton(stringResource(R.string.action_nudge), { sheet = HomeSheet.NUDGE }, style = if (state.watcher) OrbitButtonStyle.Primary else OrbitButtonStyle.Secondary)
                if (!state.watcher) OrbitButton(stringResource(R.string.action_trip), actions.onOpenMap, style = OrbitButtonStyle.Secondary)
            }
            actionLine(state.action)?.let { (text, isError) ->
                Text(
                    text, style = MaterialTheme.typography.bodyMedium, color = if (isError) Orbit.colors.warnInk else Orbit.colors.goodInk,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            // The watcher never shares, takes trips or raises SOS; those are for the one they watch over.
            if (!state.watcher) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        when (state.mySharing) {
                            SharingMode.LIVE -> R.string.my_sharing_live
                            SharingMode.PAUSED -> R.string.my_sharing_paused
                            SharingMode.OFF -> R.string.my_sharing_off
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge, color = Orbit.colors.text, modifier = Modifier.weight(1f),
                )
                OrbitButton(stringResource(R.string.action_manage), actions.onOpenMap, style = OrbitButtonStyle.Quiet)
            }

            if (!state.watcher) SosRow(state.mySosActive, actions.onOpenSafety)
        }
    }
    when (sheet) {
        HomeSheet.CHECK_IN -> CheckInSheet(state.partnerName, onSend = { k, t -> actions.onCheckIn(k, t); sheet = null }, onDismiss = { sheet = null })
        HomeSheet.NUDGE -> NudgeSheet(state.partnerName, onSend = { k -> actions.onNudge(k); sheet = null }, onDismiss = { sheet = null })
        null -> Unit
    }
}

private enum class HomeSheet { CHECK_IN, NUDGE }

/** Separate from the everyday actions, undecorated, and a tap only opens Safety (D-6: sending needs a hold). */
@Composable
private fun SosRow(active: Boolean, onOpenSafety: () -> Unit) {
    val description = stringResource(if (active) R.string.sos_row_active_cd else R.string.sos_row_cd)
    Box(
        Modifier
            .padding(top = Spacing.lg)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(BorderStroke(2.dp, Orbit.colors.sos), MaterialTheme.shapes.medium)
            .background(if (active) Orbit.colors.sos else Color.Transparent)
            .clickable(onClick = onOpenSafety)
            .clearAndSetSemantics { contentDescription = description; role = Role.Button; onClick { onOpenSafety(); true } },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(if (active) R.string.sos_row_active else R.string.sos_row),
            style = MaterialTheme.typography.titleMedium, color = if (active) Orbit.colors.onSos else Orbit.colors.sosText,
        )
    }
}

@Composable
private fun greeting(state: HomeUiState): String {
    val variant = copyVariantFor(state.relationship, state.greetingEnabled)
    val res = when (greetingSlot(LocalTime.now().hour)) {
        GreetingSlot.MORNING -> when (variant) { CopyVariant.WARM -> R.string.greeting_morning_warm; CopyVariant.NEUTRAL -> R.string.greeting_morning_neutral; CopyVariant.CASUAL -> R.string.greeting_morning_casual }
        GreetingSlot.AFTERNOON -> when (variant) { CopyVariant.WARM -> R.string.greeting_afternoon_warm; CopyVariant.NEUTRAL -> R.string.greeting_afternoon_neutral; CopyVariant.CASUAL -> R.string.greeting_afternoon_casual }
        GreetingSlot.EVENING -> when (variant) { CopyVariant.WARM -> R.string.greeting_evening_warm; CopyVariant.NEUTRAL -> R.string.greeting_evening_neutral; CopyVariant.CASUAL -> R.string.greeting_evening_casual }
        GreetingSlot.NIGHT -> when (variant) { CopyVariant.WARM -> R.string.greeting_night_warm; CopyVariant.NEUTRAL -> R.string.greeting_night_neutral; CopyVariant.CASUAL -> R.string.greeting_night_casual }
    }
    return stringResource(res)
}

@Composable
private fun battery(state: HomeUiState): Pair<String, Boolean>? {
    val pct = state.partnerBattery ?: return null
    val low = pct < 15 && state.partnerCharging != true
    return (if (low) stringResource(R.string.battery_low_hint, pct) else stringResource(R.string.battery_level, pct)) to low
}

@Composable
private fun actionLine(status: ActionStatus): Pair<String, Boolean>? = when (status) {
    ActionStatus.Idle -> null
    is ActionStatus.Sending -> stringResource(R.string.status_sending) to false
    is ActionStatus.Saved -> stringResource(if (status.action == Action.CHECK_IN) R.string.status_saved_checkin else R.string.status_saved_nudge) to false
    is ActionStatus.Failed -> failureMessage(status.reason) to true
}

@Composable
fun kindLabel(kind: String, text: String?): String = when (kind) {
    "home_safe" -> stringResource(R.string.kind_home_safe)
    "on_my_way" -> stringResource(R.string.kind_on_my_way)
    "thinking_of_you" -> stringResource(R.string.kind_thinking_of_you)
    "call_me" -> stringResource(R.string.kind_call_me)
    "are_you_ok" -> stringResource(R.string.kind_are_you_ok)
    "arrived" -> if (text != null) stringResource(R.string.kind_arrived_at, text) else stringResource(R.string.kind_arrived)
    else -> text ?: stringResource(R.string.kind_custom)
}
