package com.varad.unstoppableash.feature.safety

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.domain.model.EmergencyContact
import com.varad.unstoppableash.ui.components.OrbitButton
import com.varad.unstoppableash.ui.components.OrbitButtonStyle
import com.varad.unstoppableash.ui.components.OrbitCard
import com.varad.unstoppableash.ui.components.OrbitDialog
import com.varad.unstoppableash.ui.components.OrbitTextField
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.Spacing

data class ContactsActions(
    val onBack: () -> Unit = {},
    val onPick: () -> Unit = {},
    val onAdd: (name: String, number: String) -> Unit = { _, _ -> },
    val onRemove: (id: String) -> Unit = {},
)

@Composable
fun ContactsScreen(state: ContactsUiState, actions: ContactsActions, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val c = Orbit.colors
    Column(
        modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), tint = c.text) }
            Text(stringResource(R.string.contacts_title), style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.weight(1f).semantics { heading() })
            Text(stringResource(R.string.contacts_count, state.contacts.size, ContactsViewModel.MAX_CONTACTS), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        }
        Text(stringResource(R.string.contacts_explain), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        state.contacts.forEach { contact -> ContactRow(contact) { removing = contact.id } }
        state.notice?.let {
            Text(
                stringResource(
                    when (it) {
                        ContactsNotice.DUPLICATE -> R.string.contacts_duplicate
                        ContactsNotice.FULL -> R.string.contacts_full
                        ContactsNotice.INVALID -> R.string.contacts_invalid
                    },
                ),
                color = c.warnInk, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val full = state.contacts.size >= ContactsViewModel.MAX_CONTACTS
        OrbitButton(stringResource(R.string.contacts_pick), actions.onPick, Modifier.fillMaxWidth(), enabled = !full)
        Text(stringResource(R.string.contacts_type), style = MaterialTheme.typography.titleSmall, color = c.text)
        OrbitTextField(name, { name = it }, stringResource(R.string.contacts_name), Modifier.fillMaxWidth(), maxLength = 40)
        OrbitTextField(number, { number = it }, stringResource(R.string.contacts_number), Modifier.fillMaxWidth())
        OrbitButton(
            stringResource(R.string.action_add), { actions.onAdd(name, number); name = ""; number = "" },
            Modifier.fillMaxWidth(), style = OrbitButtonStyle.Secondary, enabled = number.isNotBlank() && !full,
        )
    }
    val target = state.contacts.firstOrNull { it.id == removing }
    if (target != null) {
        OrbitDialog(
            title = stringResource(R.string.contacts_remove_title, target.name),
            text = stringResource(R.string.contacts_remove_body),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = { actions.onRemove(target.id); removing = null },
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = { removing = null },
            destructive = true,
        )
    }
}

@Composable
private fun ContactRow(contact: EmergencyContact, onRemove: () -> Unit) {
    OrbitCard(Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(contact.name, style = MaterialTheme.typography.titleMedium, color = Orbit.colors.text)
                Text(contact.number, style = MaterialTheme.typography.bodyMedium, color = Orbit.colors.textSecondary)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, stringResource(R.string.contacts_remove, contact.name), tint = Orbit.colors.textSecondary) }
        }
    }
}
