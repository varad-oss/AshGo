package com.varad.unstoppableash.feature.fakecall

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.ui.components.OrbitButton
import com.varad.unstoppableash.ui.components.OrbitButtonStyle
import com.varad.unstoppableash.ui.components.OrbitCard
import com.varad.unstoppableash.ui.components.OrbitTextField
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.Spacing
import kotlinx.coroutines.delay

data class FakeCallActions(
    val onBack: () -> Unit = {},
    val onCaller: (String) -> Unit = {},
    val onSelectVoice: (String?) -> Unit = {},
    val onAddVoice: () -> Unit = {},
    val onRemoveVoice: (String) -> Unit = {},
    val onDelay: (CallDelay) -> Unit = {},
    val onStart: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onAccept: () -> Unit = {},
    val onDecline: () -> Unit = {},
    val onEnd: () -> Unit = {},
)

@Composable
fun FakeCallScreen(state: FakeCallUiState, actions: FakeCallActions, uptime: () -> Long, modifier: Modifier = Modifier) {
    val caller = state.caller.ifBlank { stringResource(R.string.fake_call_default_caller) }
    when (val phase = state.phase) {
        CallPhase.Setup -> Setup(state, actions, modifier)
        is CallPhase.Waiting -> {
            val left = ticking(uptime) { ((phase.ringsAtUptime - it) / 1000).coerceAtLeast(0) }
            Column(modifier.fillMaxSize().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(48.dp))
                Text(stringResource(R.string.fake_call_waiting, left), style = MaterialTheme.typography.titleLarge, color = Orbit.colors.text)
                Text(stringResource(R.string.fake_call_waiting_hint), style = MaterialTheme.typography.bodyMedium, color = Orbit.colors.textSecondary)
                OrbitButton(stringResource(R.string.action_cancel), actions.onCancel, style = OrbitButtonStyle.Secondary)
            }
        }
        CallPhase.Ringing -> CallFace(caller, stringResource(R.string.fake_call_incoming), modifier) {
            RoundAction(stringResource(R.string.fake_call_decline), CallRed, actions.onDecline)
            RoundAction(stringResource(R.string.fake_call_accept), CallGreen, actions.onAccept)
        }
        is CallPhase.InCall -> {
            val secs = ticking(uptime) { ((it - phase.startedAtUptime) / 1000).coerceAtLeast(0) }
            CallFace(caller, "%d:%02d".format(secs / 60, secs % 60), modifier) {
                RoundAction(stringResource(R.string.fake_call_end), CallRed, actions.onEnd)
            }
        }
    }
}

@Composable
private fun ticking(uptime: () -> Long, f: (Long) -> Long): Long {
    var now by remember { mutableLongStateOf(uptime()) }
    LaunchedEffect(Unit) { while (true) { now = uptime(); delay(250) } }
    return f(now)
}

private val CallBg = Color(0xFF15151A)
private val CallRed = Color(0xFFD93A3A)
private val CallGreen = Color(0xFF2E9E5B)

/** Deliberately generic: no OEM look, nothing that says "AshGo" while it's on screen. */
@Composable
private fun CallFace(caller: String, subtitle: String, modifier: Modifier, buttons: @Composable () -> Unit) {
    Column(
        modifier.fillMaxSize().background(CallBg).padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(96.dp))
        Box(Modifier.size(96.dp).background(Color(0xFF3A3A44), CircleShape), contentAlignment = Alignment.Center) {
            Text(caller.take(1).uppercase(), color = Color.White, fontSize = 40.sp)
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(caller, color = Color.White, fontSize = 32.sp, modifier = Modifier.semantics { heading() })
        Text(subtitle, color = Color(0xFFB8B8C4), fontSize = 18.sp)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(bottom = 48.dp), horizontalArrangement = Arrangement.SpaceEvenly) { buttons() }
    }
}

@Composable
private fun RoundAction(label: String, color: Color, onClick: () -> Unit) {
    // One target for circle and label, announced by its label (a bare circle would be nameless to TalkBack).
    Column(
        Modifier.clickable(role = Role.Button, onClick = onClick).padding(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).background(color, CircleShape))
        Spacer(Modifier.height(Spacing.sm))
        Text(label, color = Color.White)
    }
}

@Composable
private fun Setup(state: FakeCallUiState, actions: FakeCallActions, modifier: Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrbitButton(stringResource(R.string.action_back), actions.onBack, style = OrbitButtonStyle.Quiet)
            Text(stringResource(R.string.fake_call_title), style = MaterialTheme.typography.titleLarge, color = Orbit.colors.text)
        }
        Text(stringResource(R.string.fake_call_intro), style = MaterialTheme.typography.bodyMedium, color = Orbit.colors.textSecondary)
        OrbitTextField(state.caller, actions.onCaller, stringResource(R.string.fake_call_caller), Modifier.fillMaxWidth(), maxLength = 40)
        OrbitCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.fake_call_voice), style = MaterialTheme.typography.titleSmall, color = Orbit.colors.text)
            VoiceRow(stringResource(R.string.fake_call_builtin), state.selectedVoiceId == null, { actions.onSelectVoice(null) }, null)
            state.voices.forEach { v -> VoiceRow(v.name, state.selectedVoiceId == v.id, { actions.onSelectVoice(v.id) }) { actions.onRemoveVoice(v.id) } }
            OrbitButton(stringResource(R.string.fake_call_add_voice), actions.onAddVoice, style = OrbitButtonStyle.Quiet)
            if (state.addingFailed) Text(stringResource(R.string.fake_call_add_failed), style = MaterialTheme.typography.bodySmall, color = Orbit.colors.sosText)
            Text(stringResource(R.string.fake_call_voice_private), style = MaterialTheme.typography.bodySmall, color = Orbit.colors.textSecondary)
        }
        Text(stringResource(R.string.fake_call_when), style = MaterialTheme.typography.labelLarge, color = Orbit.colors.text)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for ((d, label) in listOf(CallDelay.NOW to R.string.fake_call_now, CallDelay.TEN_SECONDS to R.string.fake_call_10s, CallDelay.ONE_MINUTE to R.string.fake_call_1m)) {
                FilterChip(selected = state.delay == d, onClick = { actions.onDelay(d) }, label = { Text(stringResource(label)) })
            }
        }
        OrbitButton(stringResource(R.string.fake_call_start), actions.onStart, Modifier.fillMaxWidth())
    }
}

@Composable
private fun VoiceRow(name: String, selected: Boolean, onSelect: () -> Unit, onRemove: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).selectable(selected, role = Role.RadioButton, onClick = onSelect), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Text(name, Modifier.padding(start = Spacing.sm), color = Orbit.colors.text)
        }
        onRemove?.let { OrbitButton(stringResource(R.string.action_remove), it, style = OrbitButtonStyle.Quiet) }
    }
}
