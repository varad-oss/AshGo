package com.varad.unstoppableash.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.Spacing

@Composable
fun EmptyState(
    illustration: Painter?,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (illustration != null) Image(illustration, contentDescription = null, modifier = Modifier.size(120.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Orbit.colors.text, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Orbit.colors.textSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) OrbitButton(actionLabel, onAction, style = OrbitButtonStyle.Secondary)
    }
}

/** Recoverable problem. Uses the uncertainty colours — red is reserved for emergencies. */
@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null, actionLabel: String? = null) {
    val c = Orbit.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(c.warnBg)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = c.warnInk)
        Text(message, color = c.warnInk, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (onRetry != null) OrbitButton(actionLabel ?: stringResource(R.string.action_retry), onRetry, style = OrbitButtonStyle.Quiet)
    }
}

@Composable
fun OfflineBar(modifier: Modifier = Modifier) {
    val c = Orbit.colors
    val description = stringResource(R.string.offline_bar_cd)
    Row(
        modifier
            .fillMaxWidth()
            .background(c.warnBg)
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .clearAndSetSemantics { contentDescription = description; liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = c.warnInk, modifier = Modifier.size(16.dp))
        Text(stringResource(R.string.offline_bar), color = c.warnInk, style = MaterialTheme.typography.labelMedium)
    }
}
