package com.varad.unstoppableash.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.ui.theme.Orbit

/** Avatar from a small (≤ 30 KB) Base64 JPEG, decoded once per value; initials on blush otherwise. */
@Composable
fun AvatarImage(name: String, base64: String?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val bitmap: ImageBitmap? = remember(base64) { base64?.let(::decodeAvatar) }
    val c = Orbit.colors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(c.blush)
            .clearAndSetSemantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Text(initials(name), color = c.text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

internal fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.substring(0, it.offsetByCodePoints(0, 1)).uppercase() }

private fun decodeAvatar(base64: String): ImageBitmap? = runCatching {
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()
