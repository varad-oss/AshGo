package com.varad.unstoppableash.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.varad.unstoppableash.R
import com.varad.unstoppableash.data.update.UpdateFailure
import com.varad.unstoppableash.ui.components.OrbitButton
import com.varad.unstoppableash.ui.components.OrbitButtonStyle
import com.varad.unstoppableash.ui.components.OrbitCard
import com.varad.unstoppableash.ui.components.OrbitDialog
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.Spacing

@Composable
private fun Page(title: String, onBack: () -> Unit, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrbitButton(stringResource(R.string.action_back), onBack, style = OrbitButtonStyle.Quiet)
            Text(title, style = MaterialTheme.typography.titleLarge, color = Orbit.colors.text, modifier = Modifier.semantics { heading() })
        }
        content()
    }
}

@Composable
private fun Block(title: Int, body: Int) {
    OrbitCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = Orbit.colors.text, modifier = Modifier.semantics { heading() })
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = Orbit.colors.textSecondary)
        }
    }
}

/** Privacy & data: what is kept and for how long (D-14), and "Delete my messages now". */
@Composable
fun PrivacyScreen(state: SettingsUiState, onBack: () -> Unit, onDeleteMessages: () -> Unit, modifier: Modifier = Modifier) {
    var confirm by remember { mutableStateOf(false) }
    Page(stringResource(R.string.settings_privacy), onBack, modifier) {
        Block(R.string.privacy_where_title, R.string.privacy_where_body)
        Block(R.string.privacy_kept_title, R.string.privacy_kept_body)
        Block(R.string.privacy_phone_title, R.string.privacy_phone_body)
        if (state.pairId != null) {
            OrbitButton(stringResource(R.string.privacy_delete_messages), { confirm = true }, style = OrbitButtonStyle.Secondary, loading = state.deleting)
        }
        state.deletedCount?.let {
            Text(
                pluralStringResource(R.plurals.privacy_deleted, it, it),
                style = MaterialTheme.typography.bodyMedium, color = Orbit.colors.text,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    if (confirm) {
        OrbitDialog(
            title = stringResource(R.string.privacy_delete_messages),
            text = stringResource(R.string.privacy_delete_messages_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { confirm = false; onDeleteMessages() },
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = { confirm = false },
            destructive = true,
        )
    }
}

/** Updates (D-10): every step is shown, and nothing installs unless its checksum matches. */
@Composable
fun UpdatesScreen(state: UpdatesState, versionName: String, onBack: () -> Unit, onCheck: () -> Unit, onDownload: () -> Unit, modifier: Modifier = Modifier) {
    Page(stringResource(R.string.settings_updates), onBack, modifier) {
        Text(stringResource(R.string.updates_installed, versionName), style = MaterialTheme.typography.bodyLarge, color = Orbit.colors.text)
        val status = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        when (state) {
            UpdatesState.Idle -> OrbitButton(stringResource(R.string.updates_check), onCheck)
            UpdatesState.Checking -> OrbitButton(stringResource(R.string.updates_check), onCheck, loading = true)
            UpdatesState.UpToDate -> {
                Text(stringResource(R.string.updates_up_to_date), status, color = Orbit.colors.text)
                OrbitButton(stringResource(R.string.updates_check), onCheck, style = OrbitButtonStyle.Secondary)
            }
            UpdatesState.CannotCheck -> {
                Text(stringResource(R.string.updates_cannot_check), status, color = Orbit.colors.text)
                OrbitButton(stringResource(R.string.action_retry), onCheck, style = OrbitButtonStyle.Secondary)
            }
            is UpdatesState.Found -> {
                Text(stringResource(R.string.updates_found, state.info.versionName ?: state.info.versionCode.toString()), status, color = Orbit.colors.text)
                state.info.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Orbit.colors.textSecondary) }
                OrbitButton(stringResource(R.string.updates_download), onDownload)
            }
            is UpdatesState.Downloading -> {
                Text(stringResource(R.string.updates_downloading), status, color = Orbit.colors.text)
                val p = state.progress
                if (p == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            }
            UpdatesState.Installing -> Text(stringResource(R.string.updates_installing), status, color = Orbit.colors.text)
            is UpdatesState.Failed -> {
                Text(
                    stringResource(
                        when (state.reason) {
                            UpdateFailure.NETWORK -> R.string.updates_failed_network
                            UpdateFailure.NOT_ALLOWED -> R.string.updates_failed_source
                            UpdateFailure.CHECKSUM -> R.string.updates_failed_checksum
                            UpdateFailure.STORAGE -> R.string.updates_failed_storage
                        },
                    ),
                    status, color = Orbit.colors.sosText,
                )
                OrbitButton(stringResource(R.string.action_retry), onCheck, style = OrbitButtonStyle.Secondary)
            }
        }
        Text(stringResource(R.string.updates_how), style = MaterialTheme.typography.bodySmall, color = Orbit.colors.textSecondary)
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Page(stringResource(R.string.settings_help), onBack, modifier) {
        Block(R.string.help_status_title, R.string.help_status_body)
        Block(R.string.help_reliability_title, R.string.help_reliability_body)
        Block(R.string.help_sos_title, R.string.help_sos_body)
        Block(R.string.help_sms_title, R.string.help_sms_body)
        Block(R.string.privacy_where_title, R.string.privacy_where_body)
    }
}

@Composable
fun AboutScreen(versionName: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Page(stringResource(R.string.settings_about), onBack, modifier) {
        Text(stringResource(R.string.about_version, versionName), style = MaterialTheme.typography.bodyLarge, color = Orbit.colors.text)
        Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium, color = Orbit.colors.textSecondary)
        Block(R.string.about_licences_title, R.string.about_licences_body)
    }
}
