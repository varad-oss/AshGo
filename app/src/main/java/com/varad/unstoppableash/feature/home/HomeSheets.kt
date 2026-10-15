package com.varad.unstoppableash.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.varad.unstoppableash.R
import com.varad.unstoppableash.ui.components.OrbitButton
import com.varad.unstoppableash.ui.components.OrbitButtonStyle
import com.varad.unstoppableash.ui.components.OrbitSheet
import com.varad.unstoppableash.ui.components.OrbitTextField
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.Spacing

@Composable
fun CheckInSheet(partnerName: String, onSend: (kind: String, text: String?) -> Unit, onDismiss: () -> Unit) {
    var note by remember { mutableStateOf("") }
    OrbitSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.checkin_sheet_title, partnerName), style = MaterialTheme.typography.titleLarge, color = Orbit.colors.text)
            for (kind in listOf("home_safe", "on_my_way", "thinking_of_you")) {
                OrbitButton(kindLabel(kind, null), { onSend(kind, null) }, Modifier.fillMaxWidth(), style = OrbitButtonStyle.Secondary)
            }
            OrbitTextField(note, { note = it }, stringResource(R.string.checkin_custom_label), Modifier.fillMaxWidth(), maxLength = 60)
            OrbitButton(stringResource(R.string.action_send), { onSend("custom", note.trim()) }, Modifier.fillMaxWidth(), enabled = note.isNotBlank())
        }
    }
}

@Composable
fun NudgeSheet(partnerName: String, onSend: (kind: String) -> Unit, onDismiss: () -> Unit) {
    OrbitSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.nudge_sheet_title, partnerName), style = MaterialTheme.typography.titleLarge, color = Orbit.colors.text)
            for (kind in listOf("thinking_of_you", "call_me", "are_you_ok")) {
                OrbitButton(kindLabel(kind, null), { onSend(kind) }, Modifier.fillMaxWidth(), style = OrbitButtonStyle.Secondary)
            }
        }
    }
}
