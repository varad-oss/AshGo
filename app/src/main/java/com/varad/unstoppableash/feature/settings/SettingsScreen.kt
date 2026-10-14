package com.varad.unstoppableash.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.ui.components.AvatarImage
import com.varad.unstoppableash.ui.components.ErrorBanner
import com.varad.unstoppableash.ui.components.OrbitButton
import com.varad.unstoppableash.ui.components.OrbitButtonStyle
import com.varad.unstoppableash.ui.components.OrbitCard
import com.varad.unstoppableash.ui.components.OrbitDialog
import com.varad.unstoppableash.ui.components.failureMessage
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.Spacing
import com.varad.unstoppableash.ui.theme.ThemeMode

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onLeave: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onDismissFailure: () -> Unit = {},
    val onOpenReliability: () -> Unit = {},
)

/** Stage 5 settings shell; the remaining sections (contacts, reliability, privacy…) arrive in Stages 7–10. */
@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    var confirmLeave by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrbitButton(stringResource(R.string.action_back), actions.onBack, style = OrbitButtonStyle.Quiet)
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, color = Orbit.colors.text)
        }
        state.failure?.let { ErrorBanner(failureMessage(it), onRetry = actions.onDismissFailure, actionLabel = stringResource(R.string.action_dismiss)) }
        OrbitCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                AvatarImage(state.myName, state.myAvatar, size = 56.dp)
                Column {
                    Text(state.myName, style = MaterialTheme.typography.titleMedium, color = Orbit.colors.text)
                    state.myEmail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Orbit.colors.textSecondary) }
                    if (state.partnerName.isNotEmpty()) Text("♡ ${state.partnerName}", style = MaterialTheme.typography.bodyMedium, color = Orbit.colors.textSecondary)
                }
            }
        }
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelLarge, color = Orbit.colors.text)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for ((mode, label) in listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)) {
                FilterChip(selected = state.themeMode == mode, onClick = { actions.onTheme(mode) }, label = { Text(stringResource(label)) })
            }
        }
        OrbitButton(stringResource(R.string.settings_reliability), actions.onOpenReliability, style = OrbitButtonStyle.Secondary)
        if (state.pairId != null) {
            OrbitButton(stringResource(R.string.settings_leave), { confirmLeave = true }, style = OrbitButtonStyle.Secondary, loading = state.busy)
        }
        OrbitButton(stringResource(R.string.settings_sign_out), actions.onSignOut, style = OrbitButtonStyle.Quiet, loading = state.signingOut)
    }
    if (confirmLeave) {
        OrbitDialog(
            title = stringResource(R.string.settings_leave),
            text = stringResource(R.string.settings_leave_body),
            confirmLabel = stringResource(R.string.settings_leave),
            onConfirm = { confirmLeave = false; actions.onLeave() },
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = { confirmLeave = false },
            destructive = true,
        )
    }
}
