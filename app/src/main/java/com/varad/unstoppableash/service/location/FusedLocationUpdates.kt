package com.varad.unstoppableash.service.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
