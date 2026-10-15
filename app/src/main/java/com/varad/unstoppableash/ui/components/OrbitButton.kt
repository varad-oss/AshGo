package com.varad.unstoppableash.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.ui.theme.Orbit

enum class OrbitButtonStyle { Primary, Secondary, Quiet }

@Composable
fun OrbitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OrbitButtonStyle = OrbitButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val loadingLabel = stringResource(R.string.loading)
    val m = modifier
        .heightIn(min = 48.dp)
        .graphicsLayer { val s = if (pressed) 0.98f else 1f; scaleX = s; scaleY = s }
        .semantics { if (loading) stateDescription = loadingLabel }
    val guarded = { if (!loading) onClick() }
    val c = Orbit.colors
    val content: @Composable () -> Unit = {
        if (loading) {
            LoadingOrbit(color = if (style == OrbitButtonStyle.Primary) c.onPrimary else c.primary)
        } else {
            Text(text)
        }
    }
    when (style) {
        OrbitButtonStyle.Primary -> Button(
            onClick = guarded, modifier = m, enabled = enabled, shape = CircleShape, interactionSource = interaction,
            colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
        ) { content() }
        OrbitButtonStyle.Secondary -> OutlinedButton(
            onClick = guarded, modifier = m, enabled = enabled, shape = CircleShape, interactionSource = interaction,
            border = BorderStroke(1.dp, if (enabled) c.primary else c.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.primary),
        ) { content() }
        OrbitButtonStyle.Quiet -> TextButton(
            onClick = guarded, modifier = m, enabled = enabled, shape = CircleShape, interactionSource = interaction,
            colors = ButtonDefaults.textButtonColors(contentColor = c.primary),
        ) { content() }
    }
}
