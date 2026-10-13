package com.varad.unstoppableash.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.Spacing

/** Flat card: 1 dp outline instead of a shadow (REDESIGN_SPEC §4). */
@Composable
fun OrbitCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Orbit.colors
    val body: @Composable () -> Unit = { Column(Modifier.padding(Spacing.lg), content = content) }
    if (onClick != null) {
        Surface(
            onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium,
            color = c.surface, contentColor = c.text, border = BorderStroke(1.dp, c.outline),
        ) { body() }
    } else {
        Surface(
            modifier = modifier, shape = MaterialTheme.shapes.medium,
            color = c.surface, contentColor = c.text, border = BorderStroke(1.dp, c.outline),
        ) { body() }
    }
}
