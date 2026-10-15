package com.varad.unstoppableash.service.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.varad.unstoppableash.domain.location.LocationAccuracy
import com.varad.unstoppableash.domain.location.LocationCadence
import com.varad.unstoppableash.domain.model.LocationFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Continuous fixes at a cadence from CadencePolicy. Callers check the location permission first. */
class FusedLocationUpdates(private val context: Context) : LocationUpdates {
    @SuppressLint("MissingPermission")
    override fun updates(cadence: LocationCadence): Flow<LocationFix> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val priority = if (cadence.accuracy == LocationAccuracy.HIGH) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val request = LocationRequest.Builder(priority, cadence.intervalMs)
            .setMinUpdateDistanceMeters(cadence.minDistanceM)
            .setMinUpdateIntervalMillis(cadence.intervalMs / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(LocationFix(it.latitude, it.longitude, it.accuracy.takeIf { a -> it.hasAccuracy() && a > 0f }, it.time)) }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        // The first periodic fix can take a whole interval (3 min when idle); turning sharing on should show
        // where I am now, so ask for one fix straight away as well.
        val cts = CancellationTokenSource()
        // One-off, so high accuracy is affordable; a balanced fix can be unavailable with only GPS (no Wi-Fi/cell).
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).addOnSuccessListener { loc ->
            loc?.let { trySend(LocationFix(it.latitude, it.longitude, it.accuracy.takeIf { a -> it.hasAccuracy() && a > 0f }, it.time)) }
        }
        awaitClose { cts.cancel(); client.removeLocationUpdates(callback) }
    }
}
