package com.varad.unstoppableash.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.Spacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Immutable
data class HoldColors(val container: Color, val content: Color, val track: Color, val progress: Color)

object HoldDefaults {
    /** SOS: red container, white ring. */
    @Composable
    fun sos(): HoldColors = Orbit.colors.let { HoldColors(it.sos, it.onSos, it.onSos.copy(alpha = 0.3f), it.onSos) }

    /** Calm hold, e.g. "I'm safe: cancel SOS". */
    @Composable
    fun primary(): HoldColors = Orbit.colors.let { HoldColors(it.primary, it.onPrimary, it.onPrimary.copy(alpha = 0.3f), it.onPrimary) }
}

/**
 * Fires [onConfirmed] only after a continuous [holdMillis] press (PRODUCT_DECISIONS D-6).
 * Releasing early sends nothing and shows a hint. Timing is a coroutine delay, so the
 * boundary is exact; the ring is driven by frame time and stays visible with animations off.
 * TalkBack users get a custom action with the same [label].
 */
@Composable
fun HoldToConfirmButton(
    label: String,
    holdMillis: Long,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    colors: HoldColors = HoldDefaults.sos(),
    size: Dp = 120.dp,
    enabled: Boolean = true,
    visibleText: String = label,
) {
    val confirm by rememberUpdatedState(onConfirmed)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var releasedEarly by remember { mutableStateOf(false) }

    LaunchedEffect(holding) {
        if (!holding) { progress = 0f; return@LaunchedEffect }
        val start = withFrameMillis { it }
        while (progress < 1f) {
            withFrameMillis { t -> progress = ((t - start).toFloat() / holdMillis).coerceAtMost(1f) }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.container)
                .alpha(if (enabled) 1f else 0.38f)
                .semantics {
                    role = Role.Button
                    contentDescription = label
                    if (enabled) {
                        customActions = listOf(CustomAccessibilityAction(label) { confirm(); true })
                    } else {
                        disabled()
                    }
                }
                .pointerInput(enabled, holdMillis) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        releasedEarly = false
                        holding = true
                        var confirmed = false
                        val timer: Job = scope.launch {
                            val wholeSeconds = (holdMillis / 1_000).toInt()
                            repeat((wholeSeconds - 1).coerceAtLeast(0)) {
                                delay(1_000)
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            delay(holdMillis - (wholeSeconds - 1).coerceAtLeast(0) * 1_000L)
                            confirmed = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            confirm()
                        }
                        waitForUpOrCancellation()
                        timer.cancel()
                        holding = false
                        releasedEarly = !confirmed
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                drawArc(colors.track, 0f, 360f, useCenter = false, style = stroke)
                if (progress > 0f) drawArc(colors.progress, -90f, 360f * progress, useCenter = false, style = stroke)
            }
            Text(
                visibleText,
                color = colors.content,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(Spacing.lg),
            )
        }
        if (releasedEarly) {
            Text(
                stringResource(R.string.hold_released_early),
                color = Orbit.colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
