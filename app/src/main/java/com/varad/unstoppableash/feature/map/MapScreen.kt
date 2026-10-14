package com.varad.unstoppableash.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.domain.location.PauseChoice
import com.varad.unstoppableash.domain.model.LocationFix
import com.varad.unstoppableash.domain.model.SharingMode
import com.varad.unstoppableash.domain.model.TripDestination
import com.varad.unstoppableash.domain.trips.OverduePhase
import com.varad.unstoppableash.ui.components.OrbitButton
import com.varad.unstoppableash.ui.components.OrbitButtonStyle
import com.varad.unstoppableash.ui.components.OrbitCard
import com.varad.unstoppableash.ui.components.OrbitSheet
import com.varad.unstoppableash.ui.components.OrbitTextField
import com.varad.unstoppableash.ui.components.StatusPill
import com.varad.unstoppableash.ui.components.shortAgeAgo
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the map draws. Built from [MapUiState]; the map widget itself is swappable so tests never load tiles. */
data class MapPins(val me: LocationFix?, val partner: LocationFix?, val partnerName: String, val partnerFaded: Boolean, val destination: TripDestination?)

/** A one-shot "centre on this" request; a new [id] moves the camera again even to the same place. */
data class MapFocus(val lat: Double, val lng: Double, val id: Long)

data class MapActions(
    val onShareLive: () -> Unit = {},
    val onPause: (PauseChoice) -> Unit = {},
    val onStopSharing: () -> Unit = {},
    val onStartTrip: (dest: TripDestination?, expectBy: Long?, note: String?, notifyPartner: Boolean) -> Unit = { _, _, _, _ -> },
    val onExtendTrip: () -> Unit = {},
    val onArrive: () -> Unit = {},
    val onEndTrip: () -> Unit = {},
    /** Opens Safety: help is always a deliberate hold there, never sent from here. */
    val onGetHelp: () -> Unit = {},
)

typealias MapContent = @Composable (pins: MapPins, focus: MapFocus?, onLongPress: (Double, Double) -> Unit, modifier: Modifier) -> Unit

@Composable
fun MapScreen(state: MapUiState, actions: MapActions, map: MapContent, modifier: Modifier = Modifier) {
    var focus by remember { mutableStateOf<MapFocus?>(null) }
    var picked by remember { mutableStateOf<TripDestination?>(null) }
    var tripSheet by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize().background(Orbit.colors.background)) {
        map(
            MapPins(state.myFix, state.partnerFix, state.partnerName, state.partnerFaded, state.myTrip?.dest ?: picked),
            focus,
            { lat, lng -> picked = TripDestination(lat, lng, null); tripSheet = true },
            Modifier.fillMaxSize(),
        )
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Header(state)
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
                    // Solid backing: outlined buttons alone are unreadable over busy map tiles.
                    Surface(color = Orbit.colors.surface, shape = MaterialTheme.shapes.extraLarge) {
                        OrbitButton(stringResource(R.string.map_center_me), { state.myFix?.let { focus = MapFocus(it.lat, it.lng, System.nanoTime()) } }, enabled = state.myFix != null, style = OrbitButtonStyle.Secondary)
                    }
                    Surface(color = Orbit.colors.surface, shape = MaterialTheme.shapes.extraLarge) {
                        OrbitButton(stringResource(R.string.map_center_partner, state.partnerName), { state.partnerFix?.let { focus = MapFocus(it.lat, it.lng, System.nanoTime()) } }, enabled = state.partnerFix != null, style = OrbitButtonStyle.Secondary)
                    }
                }
                // OSM licence: attribution is always on screen, never behind a menu (D-7).
                Text(
                    stringResource(R.string.map_attribution), style = MaterialTheme.typography.labelSmall, color = Orbit.colors.text,
                    modifier = Modifier.padding(Spacing.sm).background(Orbit.colors.surface.copy(alpha = 0.85f)).padding(horizontal = Spacing.xs),
                )
                Panel(state, actions, onStartTrip = { tripSheet = true })
            }
        }
    }
    if (tripSheet) {
        TripSheet(state, picked, onStart = { d, e, n, t -> actions.onStartTrip(d, e, n, t); tripSheet = false; picked = null }, onDismiss = { tripSheet = false })
    }
}

@Composable
private fun Header(state: MapUiState) {
    Surface(color = Orbit.colors.surface.copy(alpha = 0.95f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(state.partnerName, style = MaterialTheme.typography.titleMedium, color = Orbit.colors.text)
            StatusPill(state.partnerName, state.partnerPresence)
        }
    }
}

@Composable
private fun Panel(state: MapUiState, actions: MapActions, onStartTrip: () -> Unit) {
    Surface(color = Orbit.colors.surface, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (state.partnerTripPhase == OverduePhase.ALERT_PARTNER) PartnerOverdue(state, actions.onGetHelp)
            if (state.myTripPhase == OverduePhase.ASK_TRAVELLER || state.myTripPhase == OverduePhase.ALERT_PARTNER) MeOverdue(actions)
            Trip(state, actions, onStartTrip)
            Sharing(state, actions)
        }
    }
}

@Composable
fun PartnerOverdueCard(partnerName: String, overdueMs: Long, lastFix: LocationFix?, nowMs: Long, onGetHelp: () -> Unit, modifier: Modifier = Modifier) {
    val c = Orbit.colors
    val where = lastFix?.let { shortAgeAgo(nowMs - it.at) }
    Surface(color = c.warnBg, contentColor = c.warnInk, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(R.string.overdue_partner, partnerName, overdueDuration(overdueMs)), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            where?.let { Text(stringResource(R.string.overdue_partner_location, it), style = MaterialTheme.typography.bodyMedium) }
            OrbitButton(stringResource(R.string.overdue_partner_help), onGetHelp, style = OrbitButtonStyle.Secondary)
        }
    }
}

@Composable
private fun PartnerOverdue(state: MapUiState, onGetHelp: () -> Unit) {
    val trip = state.partnerTrip ?: return
    val deadline = trip.extendedTo ?: trip.expectBy ?: return
    PartnerOverdueCard(state.partnerName, state.nowMs - deadline, state.partnerFix, state.nowMs, onGetHelp)
}

@Composable
private fun MeOverdue(actions: MapActions) {
    val c = Orbit.colors
    Surface(color = c.warnBg, contentColor = c.warnInk, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.overdue_me_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OrbitButton(stringResource(R.string.overdue_me_safe), actions.onArrive)
                OrbitButton(stringResource(R.string.trip_extend), actions.onExtendTrip, style = OrbitButtonStyle.Secondary)
                OrbitButton(stringResource(R.string.overdue_me_help), actions.onGetHelp, style = OrbitButtonStyle.Secondary)
            }
        }
    }
}

@Composable
private fun Trip(state: MapUiState, actions: MapActions, onStartTrip: () -> Unit) {
    val trip = state.myTrip
    if (trip == null) {
        OrbitButton(stringResource(R.string.trip_start), onStartTrip, Modifier.fillMaxWidth())
        return
    }
    val deadline = trip.extendedTo ?: trip.expectBy
    Text(
        if (deadline != null) stringResource(R.string.trip_active_until, timeOf(deadline)) else stringResource(R.string.trip_active),
        style = MaterialTheme.typography.titleMedium, color = Orbit.colors.text,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OrbitButton(stringResource(R.string.trip_arrived_button), actions.onArrive)
        OrbitButton(stringResource(R.string.trip_extend), actions.onExtendTrip, style = OrbitButtonStyle.Secondary)
        OrbitButton(stringResource(R.string.trip_end), actions.onEndTrip, style = OrbitButtonStyle.Quiet)
    }
}

@Composable
private fun Sharing(state: MapUiState, actions: MapActions) {
    val until = state.mySharingUntil
    Text(
        when (state.mySharing) {
            SharingMode.LIVE -> stringResource(R.string.sharing_live)
            SharingMode.PAUSED -> if (until != null) stringResource(R.string.sharing_paused_until, timeOf(until)) else stringResource(R.string.sharing_paused)
            SharingMode.OFF -> stringResource(R.string.sharing_off)
        },
        style = MaterialTheme.typography.bodyLarge, color = Orbit.colors.text,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (state.mySharing != SharingMode.LIVE) {
            OrbitButton(stringResource(R.string.sharing_share_live), actions.onShareLive)
        } else {
            OrbitButton(stringResource(R.string.sharing_pause_1h), { actions.onPause(PauseChoice.ONE_HOUR) }, style = OrbitButtonStyle.Secondary)
            OrbitButton(stringResource(R.string.sharing_pause_morning), { actions.onPause(PauseChoice.UNTIL_MORNING) }, style = OrbitButtonStyle.Secondary)
            OrbitButton(stringResource(R.string.sharing_pause_open), { actions.onPause(PauseChoice.UNTIL_I_RESUME) }, style = OrbitButtonStyle.Secondary)
        }
        if (state.mySharing != SharingMode.OFF) OrbitButton(stringResource(R.string.sharing_stop), actions.onStopSharing, style = OrbitButtonStyle.Quiet)
    }
}

private enum class DestChoice { NONE, HERE, MAP }
private enum class ExpectChoice(val minutes: Long?) { NONE(null), M30(30), H1(60), H2(120) }

@Composable
private fun TripSheet(state: MapUiState, picked: TripDestination?, onStart: (TripDestination?, Long?, String?, Boolean) -> Unit, onDismiss: () -> Unit) {
    var dest by rememberSaveable { mutableStateOf(if (picked != null) DestChoice.MAP else DestChoice.NONE) }
    var expect by rememberSaveable { mutableStateOf(ExpectChoice.H1) }
    var note by rememberSaveable { mutableStateOf("") }
    var tell by rememberSaveable { mutableStateOf(true) }
    OrbitSheet(onDismiss) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.trip_sheet_title), style = MaterialTheme.typography.titleLarge, color = Orbit.colors.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(dest == DestChoice.NONE, { dest = DestChoice.NONE }, { Text(stringResource(R.string.trip_dest_none)) })
                if (state.myFix != null) FilterChip(dest == DestChoice.HERE, { dest = DestChoice.HERE }, { Text(stringResource(R.string.trip_dest_here)) })
                if (picked != null) FilterChip(dest == DestChoice.MAP, { dest = DestChoice.MAP }, { Text(stringResource(R.string.trip_dest_map)) })
            }
            if (picked == null) Text(stringResource(R.string.trip_dest_hint), style = MaterialTheme.typography.bodySmall, color = Orbit.colors.textSecondary)
            Text(stringResource(R.string.trip_expect), style = MaterialTheme.typography.labelLarge, color = Orbit.colors.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ExpectChoice.entries.forEach { e ->
                    val label = when (e) {
                        ExpectChoice.NONE -> R.string.trip_expect_none; ExpectChoice.M30 -> R.string.trip_expect_30
                        ExpectChoice.H1 -> R.string.trip_expect_60; ExpectChoice.H2 -> R.string.trip_expect_120
                    }
                    FilterChip(expect == e, { expect = e }, { Text(stringResource(label)) })
                }
            }
            OrbitTextField(note, { note = it }, stringResource(R.string.trip_note), Modifier.fillMaxWidth(), maxLength = 80)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.trip_tell_partner, state.partnerName), style = MaterialTheme.typography.bodyLarge, color = Orbit.colors.text, modifier = Modifier.weight(1f))
                Switch(tell, { tell = it })
            }
            OrbitButton(stringResource(R.string.trip_go), {
                val d = when (dest) {
                    DestChoice.NONE -> null
                    DestChoice.HERE -> state.myFix?.let { TripDestination(it.lat, it.lng, null) }
                    DestChoice.MAP -> picked
                }
                onStart(d, expect.minutes?.let { state.nowMs + it * 60_000 }, note.trim().ifEmpty { null }, tell)
            }, Modifier.fillMaxWidth())
        }
    }
}

/** "12 min", "2 h": how long past the deadline. */
private fun overdueDuration(ms: Long): String {
    val m = (ms.coerceAtLeast(0) / 60_000).coerceAtLeast(1)
    return if (m < 60) "$m min" else "${m / 60} h"
}

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private fun timeOf(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(timeFormat)
