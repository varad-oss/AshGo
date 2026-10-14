package com.varad.unstoppableash.service.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.varad.unstoppableash.domain.model.LocationFix
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** "Where am I right now?" for one-off needs such as SOS. Null when unknown or not permitted — never a guess. */
fun interface LocationSource {
    suspend fun current(): LocationFix?
}

class FusedLocationSource(private val context: Context, private val timeoutMs: Long = 10_000) : LocationSource {

    @SuppressLint("MissingPermission") // checked just below
    override suspend fun current(): LocationFix? {
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!fine && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return runCatching {
            val cts = CancellationTokenSource()
            val priority = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val fresh = withTimeoutOrNull(timeoutMs) { client.getCurrentLocation(priority, cts.token).await() }
                ?: run { cts.cancel(); client.lastLocation.await() }
            fresh?.let { LocationFix(it.latitude, it.longitude, it.accuracy.takeIf { a -> it.hasAccuracy() && a > 0f }, it.time) }
        }.getOrNull()
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
}
