package com.varad.unstoppableash.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.ui.components.AvatarImage
import com.varad.unstoppableash.ui.components.ErrorBanner
import com.varad.unstoppableash.ui.components.OrbitButton
import com.varad.unstoppableash.ui.components.OrbitButtonStyle
import com.varad.unstoppableash.ui.components.OrbitCard
import com.varad.unstoppableash.ui.components.OrbitDialog
import com.varad.unstoppableash.ui.components.OrbitTextField
import com.varad.unstoppableash.ui.components.failureMessage
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.Spacing
import com.varad.unstoppableash.ui.theme.ThemeMode

/** Where a settings row leads; Contacts and Reliability are their own screens elsewhere. */
enum class SettingsPage { OUR_PAGE, CONTACTS, RELIABILITY, PRIVACY, UPDATES, HELP, ABOUT }

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onLeave: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onDismissFailure: () -> Unit = {},
    val onOpen: (SettingsPage) -> Unit = {},
    val onRename: (String) -> Unit = {},
    val onPickAvatar: () -> Unit = {},
    val onRemoveAvatar: () -> Unit = {},
    val onGreeting: (Boolean) -> Unit = {},
    val onNotify: (NotifyToggle, Boolean) -> Unit = { _, _ -> },
    /** SOS alerts are never switched off in the app; this opens the system's own channel settings. */
    val onOpenSosChannel: () -> Unit = {},
    val onFollowSharing: (Boolean) -> Unit = {},
)

@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    var confirmLeave by remember { mutableStateOf(false) }
    var editingName by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrbitButton(stringResource(R.string.action_back), actions.onBack, style = OrbitButtonStyle.Quiet)
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, color = Orbit.colors.text)
        }
        state.failure?.let { ErrorBanner(failureMessage(it), onRetry = actions.onDismissFailure, actionLabel = stringResource(R.string.action_dismiss)) }

        Section(R.string.settings_profile) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                AvatarImage(state.myName, state.myAvatar, size = 56.dp)
                Column(Modifier.weight(1f)) {
                    Text(state.myName, style = MaterialTheme.typography.titleMedium, color = Orbit.colors.text)
                    state.myEmail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Orbit.colors.textSecondary) }
                }
            }
            if (state.pairId != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OrbitButton(stringResource(R.string.settings_edit_name), { editingName = state.myName }, style = OrbitButtonStyle.Secondary)
                    OrbitButton(stringResource(R.string.settings_change_photo), actions.onPickAvatar, style = OrbitButtonStyle.Secondary, loading = state.savingAvatar)
                }
                if (state.myAvatar != null) OrbitButton(stringResource(R.string.settings_remove_photo), actions.onRemoveAvatar, style = OrbitButtonStyle.Quiet)
                ToggleRow(stringResource(R.string.settings_greeting), state.greeting, actions.onGreeting)
            }
        }

        if (state.pairId != null) Section(R.string.settings_orbit) {
            if (state.partnerName.isNotEmpty()) Text("♡ ${state.partnerName}", style = MaterialTheme.typography.bodyLarge, color = Orbit.colors.text)
            relationshipLabel(state.relationship)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Orbit.colors.textSecondary) }
            OrbitButton(stringResource(R.string.settings_leave), { confirmLeave = true }, style = OrbitButtonStyle.Secondary, loading = state.busy)
        }

        Section(R.string.settings_notifications) {
            ToggleRow(stringResource(R.string.settings_notify_chat), state.notifyChat) { actions.onNotify(NotifyToggle.CHAT, it) }
            ToggleRow(stringResource(R.string.settings_notify_nudges), state.notifyNudges) { actions.onNotify(NotifyToggle.NUDGES, it) }
            ToggleRow(stringResource(R.string.settings_notify_checkins), state.notifyCheckins) { actions.onNotify(NotifyToggle.CHECKINS, it) }
            NavRow(stringResource(R.string.settings_notify_sos), stringResource(R.string.settings_notify_sos_body), actions.onOpenSosChannel)
            if (state.watcher) {
                ToggleRow(stringResource(R.string.settings_follow_sharing, state.partnerName), state.followSharing, actions.onFollowSharing)
                Text(stringResource(R.string.settings_follow_sharing_body), style = MaterialTheme.typography.bodySmall, color = Orbit.colors.textSecondary)
            }
        }

        Section(R.string.settings_theme) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                for ((mode, label) in listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)) {
                    FilterChip(selected = state.themeMode == mode, onClick = { actions.onTheme(mode) }, label = { Text(stringResource(label)) })
                }
            }
        }

        OrbitCard(Modifier.fillMaxWidth()) {
            NavRow(stringResource(R.string.our_page_title)) { actions.onOpen(SettingsPage.OUR_PAGE) }
            NavRow(stringResource(R.string.contacts_title)) { actions.onOpen(SettingsPage.CONTACTS) }
            NavRow(stringResource(R.string.settings_reliability)) { actions.onOpen(SettingsPage.RELIABILITY) }
            NavRow(stringResource(R.string.settings_privacy)) { actions.onOpen(SettingsPage.PRIVACY) }
            NavRow(stringResource(R.string.settings_updates)) { actions.onOpen(SettingsPage.UPDATES) }
            NavRow(stringResource(R.string.settings_help)) { actions.onOpen(SettingsPage.HELP) }
            NavRow(stringResource(R.string.settings_about)) { actions.onOpen(SettingsPage.ABOUT) }
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
    editingName?.let { draft ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editingName = null },
            title = { Text(stringResource(R.string.settings_edit_name)) },
            text = { OrbitTextField(draft, { editingName = it }, stringResource(R.string.settings_name_label), maxLength = SettingsViewModel.NAME_MAX) },
            confirmButton = {
                OrbitButton(stringResource(R.string.action_save), { actions.onRename(draft); editingName = null }, enabled = draft.isNotBlank(), style = OrbitButtonStyle.Quiet)
            },
            dismissButton = { OrbitButton(stringResource(R.string.action_cancel), { editingName = null }, style = OrbitButtonStyle.Quiet) },
        )
    }
}

@Composable
private fun relationshipLabel(name: String?): String? = when (name) {
    "PARTNER" -> stringResource(R.string.relationship_partner)
    "FAMILY" -> stringResource(R.string.relationship_family)
    "FRIENDS" -> stringResource(R.string.relationship_friends)
    "TRUSTED" -> stringResource(R.string.relationship_trusted)
    else -> null
}

@Composable
private fun Section(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = Orbit.colors.text, modifier = Modifier.semantics { heading() })
        OrbitCard(Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), content = content) }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Orbit.colors.text)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun NavRow(label: String, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Orbit.colors.text)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Orbit.colors.textSecondary) }
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = Orbit.colors.textSecondary)
    }
}
