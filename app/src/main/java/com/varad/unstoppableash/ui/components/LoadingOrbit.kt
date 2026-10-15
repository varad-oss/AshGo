package com.varad.unstoppableash.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.ui.theme.Orbit
import kotlin.math.cos
import kotlin.math.sin

enum class LoadingOrbitSize(val dp: Dp) { SMALL(20.dp), LARGE(48.dp) }

/** Two dots circling each other — the Little Orbit loader. Static when animations are off. */
@Composable
fun LoadingOrbit(
    modifier: Modifier = Modifier,
    size: LoadingOrbitSize = LoadingOrbitSize.SMALL,
    color: Color = Orbit.colors.primary,
) {
    val reduced = rememberReducedMotion()
    val angle by rememberInfiniteTransition(label = "orbit").animateFloat(
        initialValue = 0f,
        targetValue = if (reduced) 0f else 360f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Restart),
        label = "orbitAngle",
    )
    val label = stringResource(R.string.loading)
    Canvas(
        modifier
            .size(size.dp)
            .semantics { contentDescription = label; liveRegion = LiveRegionMode.Polite },
    ) {
        val r = this.size.minDimension / 2 * 0.62f
        val dot = this.size.minDimension * 0.14f
        val rad = Math.toRadians(angle.toDouble())
        val dx = (cos(rad) * r).toFloat()
        val dy = (sin(rad) * r * 0.55f).toFloat()
        drawCircle(color, dot, center + Offset(dx, dy))
        drawCircle(color.copy(alpha = 0.45f), dot, center - Offset(dx, dy))
    }
}
