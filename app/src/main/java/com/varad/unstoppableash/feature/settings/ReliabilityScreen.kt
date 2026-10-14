package com.varad.unstoppableash.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.ui.components.OrbitButton
import com.varad.unstoppableash.ui.components.OrbitButtonStyle
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.Spacing

data class ReliabilityActions(val onBack: () -> Unit = {}, val onFix: (Fix) -> Unit = {}, val onTest: () -> Unit = {})

/** USER_JOURNEYS J12: each check with its real status and a one-tap fix. */
@Composable
fun ReliabilityScreen(items: List<ReliabilityItem>, actions: ReliabilityActions, modifier: Modifier = Modifier) {
    val c = Orbit.colors
    Column(
        modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), tint = c.text) }
            Text(stringResource(R.string.reliability_title), style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
        }
        Text(stringResource(R.string.reliability_intro), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        items.forEach { item ->
            val label = stringResource(labelOf(item.check))
            val description = if (item.ok) stringResource(R.string.rel_ok_cd, label) else stringResource(R.string.rel_problem, label)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = description }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Icon(if (item.ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null, tint = if (item.ok) c.goodInk else c.warnInk, modifier = Modifier.size(24.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = c.text)
                }
                if (!item.ok) OrbitButton(stringResource(R.string.action_fix), { actions.onFix(item.fix) }, style = OrbitButtonStyle.Secondary)
            }
        }
        OrbitButton(stringResource(R.string.rel_test), actions.onTest, Modifier.fillMaxWidth(), style = OrbitButtonStyle.Secondary)
    }
}

private fun labelOf(check: Check) = when (check) {
    Check.NOTIFICATIONS -> R.string.rel_notifications
    Check.SOS_CHANNEL -> R.string.rel_sos_channel
    Check.FULL_SCREEN -> R.string.rel_full_screen
    Check.BATTERY -> R.string.rel_battery
    Check.LOCATION -> R.string.rel_location
    Check.SMS -> R.string.rel_sms
    Check.CONNECTION -> R.string.rel_connection
}
