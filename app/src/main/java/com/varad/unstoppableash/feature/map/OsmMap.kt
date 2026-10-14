package com.varad.unstoppableash.feature.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.varad.unstoppableash.R
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

/**
 * osmdroid behind [MapContent] (D-7): standard OSM tiles with an honest User-Agent, the tile cache in the app's
 * cache dir, no prefetching, and attribution drawn by [MapScreen]. Pins only exist where real fixes exist.
 */
@Composable
fun OsmMap(pins: MapPins, focus: MapFocus?, onLongPress: (Double, Double) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val longPress by rememberUpdatedState(onLongPress)
    val map = remember { createMap(context) { lat, lng -> longPress(lat, lng) } }
    val me = remember { marker(map, R.drawable.ic_map_me) }
    val partner = remember { marker(map, R.drawable.ic_map_partner) }
    val destination = remember { marker(map, R.drawable.ic_map_destination) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); map.onDetach() }
    }
    LaunchedEffect(focus) { focus?.let { map.controller.animateTo(GeoPoint(it.lat, it.lng), 16.0, 600L) } }
    // Frame the first real pin once it arrives (data loads after the map); until then a wide default, never a guess.
    val first = pins.partner ?: pins.me
    val framed = remember { booleanArrayOf(false) }
    LaunchedEffect(first != null) {
        if (first != null && !framed[0]) { framed[0] = true; map.controller.setZoom(15.0); map.controller.setCenter(GeoPoint(first.lat, first.lng)) }
    }

    val meLabel = stringResource(R.string.map_me)
    val pickedLabel = stringResource(R.string.trip_dest_map)
    AndroidView(factory = { map }, modifier = modifier, update = {
        place(it, me, pins.me?.let { GeoPoint(it.lat, it.lng) }, meLabel, faded = false)
        place(it, partner, pins.partner?.let { GeoPoint(it.lat, it.lng) }, pins.partnerName, faded = pins.partnerFaded)
        place(it, destination, pins.destination?.let { GeoPoint(it.lat, it.lng) }, pins.destination?.label ?: pickedLabel, faded = false)
        it.invalidate()
    })
}

private fun createMap(context: Context, onLongPress: (Double, Double) -> Unit): MapView {
    Configuration.getInstance().apply {
        load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        userAgentValue = userAgent(context)
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
    }
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        minZoomLevel = 3.0
        controller.setZoom(5.0)
        controller.setCenter(GeoPoint(20.59, 78.96))
        overlays += MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?) = false
            override fun longPressHelper(p: GeoPoint?): Boolean { p?.let { onLongPress(it.latitude, it.longitude) }; return p != null }
        })
    }
}

/** OSM tile policy: identify the app and give a contact (D-7). */
private fun userAgent(context: Context): String {
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "dev"
    return "AshGo/$version (+https://github.com/varad-oss/AshGo)"
}

private fun marker(map: MapView, icon: Int) = Marker(map).apply {
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    this.icon = ContextCompat.getDrawable(map.context, icon)
    setInfoWindow(null)
}

private fun place(map: MapView, marker: Marker, at: GeoPoint?, title: String, faded: Boolean) {
    if (at == null) { map.overlays.remove(marker); return }
    marker.position = at
    marker.title = title
    marker.alpha = if (faded) 0.45f else 1f
    if (marker !in map.overlays) map.overlays += marker
}
