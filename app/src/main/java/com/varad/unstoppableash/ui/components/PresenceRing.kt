package com.varad.unstoppableash.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.domain.model.PresenceState
import com.varad.unstoppableash.ui.theme.Orbit

/** The two of you on one orbit; the ring takes the partner's presence colour, a small heart marks where the paths meet. */
@Composable
fun PresenceRing(
    meName: String,
    meAvatar: String?,
    partnerName: String,
    partnerAvatar: String?,
    partnerState: PresenceState,
    modifier: Modifier = Modifier,
    width: Dp = 240.dp,
    avatarSize: Dp = 72.dp,
) {
    val visual = presenceVisual(partnerName, partnerState)
    val reduced = rememberReducedMotion()
    val ringColor by animateColorAsState(visual.ink, tween(if (reduced) 0 else 200), label = "ring")
    val heart = Orbit.colors.primary
    val description = stringResource(R.string.presence_ring_cd, partnerName, visual.description)
    val height = avatarSize + 24.dp
    Box(
        modifier.size(width, height).clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = avatarSize.toPx() / 2
            drawOval(
                color = ringColor.copy(alpha = 0.55f),
                topLeft = Offset(inset, size.height * 0.2f),
                size = Size(size.width - 2 * inset, size.height * 0.6f),
                style = Stroke(width = 2.dp.toPx()),
            )
            val h = 6.dp.toPx()
            val cx = size.width / 2
            val cy = size.height * 0.8f
            val path = Path().apply {
                moveTo(cx, cy + h)
                cubicTo(cx - 2.2f * h, cy - 0.2f * h, cx - h, cy - 1.6f * h, cx, cy - 0.6f * h)
                cubicTo(cx + h, cy - 1.6f * h, cx + 2.2f * h, cy - 0.2f * h, cx, cy + h)
            }
            drawPath(path, heart)
        }
        AvatarImage(partnerName, partnerAvatar, Modifier.align(Alignment.CenterStart).offset(x = 0.dp), avatarSize)
        AvatarImage(meName, meAvatar, Modifier.align(Alignment.CenterEnd), avatarSize * 0.8f)
    }
}
