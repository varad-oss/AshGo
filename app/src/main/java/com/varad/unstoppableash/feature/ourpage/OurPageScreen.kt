package com.varad.unstoppableash.feature.ourpage

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.data.wall.WallRepository
import com.varad.unstoppableash.ui.components.EmptyState
import com.varad.unstoppableash.ui.components.OrbitButton
import com.varad.unstoppableash.ui.components.OrbitButtonStyle
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OurPageActions(val onBack: () -> Unit = {}, val onAdd: () -> Unit = {}, val onRemove: (String) -> Unit = {})

/** "Our page" (O-18): photos hung on ropes, two to a rope. They live only on this phone. */
@Composable
fun OurPageScreen(state: OurPageState, actions: OurPageActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrbitButton(stringResource(R.string.action_back), actions.onBack, style = OrbitButtonStyle.Quiet)
            Text(stringResource(R.string.our_page_title), style = MaterialTheme.typography.titleLarge, color = Orbit.colors.text)
        }
        if (state.photos.isEmpty() && !state.adding) {
            EmptyState(illustration = null, title = stringResource(R.string.our_page_empty_title), body = stringResource(R.string.our_page_empty_body))
        }
        state.photos.chunked(2).forEachIndexed { rope, pair -> Rope(rope, pair, actions.onRemove) }
        if (state.skipped > 0) Text(pluralStringResource(R.plurals.our_page_skipped, state.skipped, state.skipped), color = Orbit.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        if (state.full) {
            Text(stringResource(R.string.our_page_full, WallRepository.MAX), color = Orbit.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            OrbitButton(stringResource(R.string.our_page_add), actions.onAdd, Modifier.fillMaxWidth(), loading = state.adding)
        }
        Text(stringResource(R.string.our_page_private), color = Orbit.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Rope(index: Int, photos: List<WallPhoto>, onRemove: (String) -> Unit) {
    val ropeColor = Orbit.colors.outline
    Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val p = Path().apply { moveTo(0f, 4f); quadraticTo(size.width / 2, size.height * 1.6f, size.width, 4f) }
            drawPath(p, ropeColor, style = Stroke(width = 3f))
            listOf(0.27f, 0.73f).forEach { drawCircle(ropeColor, 6f, Offset(size.width * it, size.height * 0.62f)) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            photos.forEachIndexed { i, photo ->
                val tilt = if ((index + i) % 2 == 0) -4f else 3f
                Polaroid(photo, tilt, Modifier.weight(1f).padding(horizontal = Spacing.sm), onRemove)
            }
            if (photos.size == 1) Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Polaroid(photo: WallPhoto, tilt: Float, modifier: Modifier, onRemove: (String) -> Unit) {
    val path = photo.file
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) { bitmap = path?.let { withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() } } }
    Column(modifier.rotate(tilt), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Orbit.colors.surfaceWarm).padding(6.dp)) {
            val b = bitmap
            if (b != null) {
                Image(b, contentDescription = stringResource(R.string.our_page_photo), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(2.dp)))
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(0.8f).background(Orbit.colors.blush))
            }
        }
        OrbitButton(stringResource(R.string.action_remove), { onRemove(photo.id) }, style = OrbitButtonStyle.Quiet)
    }
}
