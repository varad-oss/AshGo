package com.varad.unstoppableash.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.domain.model.PresenceState
import com.varad.unstoppableash.domain.time.Durations.HOUR
import com.varad.unstoppableash.domain.time.Durations.MINUTE
import com.varad.unstoppableash.ui.components.EmptyState
import com.varad.unstoppableash.ui.components.ErrorBanner
import com.varad.unstoppableash.ui.components.HoldDefaults
import com.varad.unstoppableash.ui.components.HoldToConfirmButton
import com.varad.unstoppableash.ui.components.LoadingOrbit
import com.varad.unstoppableash.ui.components.LoadingOrbitSize
import com.varad.unstoppableash.ui.components.OfflineBar
import com.varad.unstoppableash.ui.components.OrbitButton
import com.varad.unstoppableash.ui.components.OrbitButtonStyle
import com.varad.unstoppableash.ui.components.OrbitCard
import com.varad.unstoppableash.ui.components.OrbitTextField
import com.varad.unstoppableash.ui.components.PresenceRing
import com.varad.unstoppableash.ui.components.ReceiptRow
import com.varad.unstoppableash.ui.components.ReceiptStatus
import com.varad.unstoppableash.ui.components.StatusPill
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.Spacing
import com.varad.unstoppableash.ui.theme.ThemeMode

/** Debug-only gallery of the v2 design system, for screenshots in both themes and at large font scales. */
class DesignCatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startDark = intent.getBooleanExtra("dark", false)
        val startScale = intent.getFloatExtra("fontScale", 1f)
        setContent {
            var dark by remember { mutableStateOf(startDark) }
            var scale by remember { mutableFloatStateOf(startScale) }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                OrbitTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    Catalog(dark, { dark = it }, scale, { scale = nextScale(scale) })
                }
            }
        }
    }

    private fun nextScale(s: Float) = when (s) { 1f -> 1.3f; 1.3f -> 2f; else -> 1f }
}

@Composable
private fun Catalog(dark: Boolean, onDark: (Boolean) -> Unit, scale: Float, onScale: () -> Unit) {
    val name = stringResource(R.string.catalog_sample_name)
    val now = System.currentTimeMillis()
    var note by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize().background(Orbit.colors.background).safeDrawingPadding().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.catalog_title), style = MaterialTheme.typography.titleLarge, color = Orbit.colors.text, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.catalog_dark), color = Orbit.colors.text)
                Switch(checked = dark, onCheckedChange = onDark)
            }
            OrbitButton(stringResource(R.string.catalog_font_scale, scale.toString()), onScale, style = OrbitButtonStyle.Quiet)
        }
        item { PresenceRing("You", null, name, null, PresenceState.Live(60_000)) }
        val states = listOf(
            PresenceState.Live(60_000), PresenceState.Recent(4 * MINUTE), PresenceState.Stale(40 * MINUTE),
            PresenceState.Stale(3 * HOUR), PresenceState.Paused(now - 2 * HOUR), PresenceState.Offline(now - 30 * MINUTE),
            PresenceState.NotSharing, PresenceState.Unknown,
        )
        states.forEach { s -> item { StatusPill(name, s) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OrbitButton(stringResource(R.string.catalog_primary), {})
                OrbitButton(stringResource(R.string.catalog_secondary), {}, style = OrbitButtonStyle.Secondary)
                OrbitButton(stringResource(R.string.catalog_quiet), {}, style = OrbitButtonStyle.Quiet)
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { OrbitButton(stringResource(R.string.catalog_primary), {}, loading = true); OrbitButton(stringResource(R.string.catalog_primary), {}, enabled = false) } }
        item { OrbitTextField(note, { note = it }, stringResource(R.string.catalog_field_label), Modifier.fillMaxWidth(), maxLength = 60) }
        item {
            OrbitCard(Modifier.fillMaxWidth()) {
                ReceiptRow(stringResource(R.string.catalog_receipt_saved), ReceiptStatus.DONE, "22:41", null)
                ReceiptRow(stringResource(R.string.catalog_receipt_partner), ReceiptStatus.PENDING, null, null)
                ReceiptRow(stringResource(R.string.catalog_receipt_seen), ReceiptStatus.UNKNOWN, null, null)
                ReceiptRow(stringResource(R.string.catalog_receipt_sms), ReceiptStatus.FAILED, null, {})
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                HoldToConfirmButton(stringResource(R.string.catalog_hold), 3_000, {}, visibleText = "SOS")
                HoldToConfirmButton(stringResource(R.string.catalog_quiet), 1_500, {}, colors = HoldDefaults.primary(), size = 96.dp)
            }
        }
        item { ErrorBanner(stringResource(R.string.catalog_error), onRetry = {}) }
        item { OfflineBar() }
        item { LoadingOrbit(size = LoadingOrbitSize.LARGE) }
        item {
            EmptyState(
                painterResource(R.drawable.ill_empty_chat),
                stringResource(R.string.catalog_empty_title),
                stringResource(R.string.catalog_empty_body),
                Modifier.fillMaxWidth(),
            )
        }
    }
}
