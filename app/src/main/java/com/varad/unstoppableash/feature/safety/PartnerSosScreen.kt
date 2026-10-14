package com.varad.unstoppableash.feature.safety

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.domain.model.SosRecordState
import com.varad.unstoppableash.ui.components.OrbitButton
import com.varad.unstoppableash.ui.components.longAgeAgo
import com.varad.unstoppableash.ui.components.shortAgeAgo
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class PartnerSosActions(
    val onAcknowledge: () -> Unit = {},
    val onCall: (String) -> Unit = {},
    val onOpenMap: (lat: Double, lng: Double) -> Unit = { _, _ -> },
    val onClose: () -> Unit = {},
)

/** REDESIGN_SPEC §PartnerSos: full red while active, calm once cancelled. Neutral copy. */
@Composable
fun PartnerSosScreen(state: PartnerSosUiState, partnerPhone: String?, nowMs: Long, actions: PartnerSosActions, modifier: Modifier = Modifier) {
    val r = state.record
    val c = Orbit.colors
    if (r == null || r.state == SosRecordState.CANCELLED) {
        Column(modifier.fillMaxSize().background(c.background).padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Text(
                if (r?.cancelledAt != null) stringResource(R.string.psos_cancelled, state.partnerName, timeOf(r.cancelledAt)) else stringResource(R.string.psos_none),
                style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
            )
            OrbitButton(stringResource(R.string.action_done), actions.onClose, Modifier.fillMaxWidth())
        }
        return
    }
    Column(
        modifier.fillMaxSize().background(c.sos).verticalScroll(rememberScrollState()).padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            stringResource(R.string.psos_title, state.partnerName), style = MaterialTheme.typography.displaySmall, color = c.onSos,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Assertive },
        )
        Text(stringResource(R.string.psos_sent, timeOf(r.at), longAgeAgo(nowMs - r.at)), style = MaterialTheme.typography.titleMedium, color = c.onSos)
        val lat = r.lat
        val lng = r.lng
        if (lat != null && lng != null) {
            val age = shortAgeAgo(nowMs - (r.locAt ?: r.at))
            val acc = r.accuracyM?.toInt()
            Text(if (acc != null) stringResource(R.string.psos_location_acc, age, acc) else stringResource(R.string.psos_location, age), style = MaterialTheme.typography.bodyLarge, color = c.onSos)
            OutlinedButton({ actions.onOpenMap(lat, lng) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = c.onSos)) {
                Text(stringResource(R.string.psos_open_map))
            }
        } else {
            Text(stringResource(R.string.psos_location_unknown), style = MaterialTheme.typography.bodyLarge, color = c.onSos)
        }
        r.battery?.let { Text(stringResource(R.string.psos_battery, it), style = MaterialTheme.typography.bodyLarge, color = c.onSos) }
        if (state.acked) {
            Text(stringResource(R.string.psos_acked, state.partnerName), style = MaterialTheme.typography.titleMedium, color = c.onSos, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        } else {
            // White on SOS red: the one primary action, unmistakable.
            Button(actions.onAcknowledge, Modifier.fillMaxWidth().padding(vertical = Spacing.xs), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = c.sosText)) {
                Text(stringResource(R.string.psos_ack), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
        if (partnerPhone != null) {
            OutlinedButton({ actions.onCall(partnerPhone) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = c.onSos)) {
                Text(stringResource(R.string.call_partner, state.partnerName))
            }
        }
        OutlinedButton({ actions.onCall(EMERGENCY_NUMBER) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = c.onSos)) {
            Text(stringResource(R.string.call_112))
        }
    }
}

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private fun timeOf(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(timeFormat)
