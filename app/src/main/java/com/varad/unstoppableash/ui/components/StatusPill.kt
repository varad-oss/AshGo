package com.varad.unstoppableash.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.domain.model.PresenceState
import com.varad.unstoppableash.ui.theme.Spacing

/** Partner presence: icon + colour + words, with a full spoken sentence for TalkBack. */
@Composable
fun StatusPill(name: String, state: PresenceState, modifier: Modifier = Modifier) {
    val v = presenceVisual(name, state)
    val reduced = rememberReducedMotion()
    val container by animateColorAsState(v.container, tween(if (reduced) 0 else 200), label = "pillBg")
    Row(
        modifier
            .clip(CircleShape)
            .background(container)
            .then(
                if (v.border == PillBorder.NONE) Modifier else Modifier.drawBehind {
                    val dash = if (v.border == PillBorder.DASHED) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
                    drawRoundRect(
                        color = v.ink,
                        cornerRadius = CornerRadius(size.height / 2),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
                    )
                },
            )
            .padding(horizontal = Spacing.md, vertical = 6.dp)
            .clearAndSetSemantics {
                contentDescription = v.description
                text = AnnotatedString(v.text)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(v.icon, contentDescription = null, tint = v.ink, modifier = Modifier.size(14.dp))
        Text(v.text, color = v.ink, style = MaterialTheme.typography.labelLarge)
    }
}
