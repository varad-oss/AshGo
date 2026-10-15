package com.varad.unstoppableash.domain.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_MEAN_RADIUS_M = 6_371_008.8

/** Great-circle (haversine) distance in metres. */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dLat = p2 - p1
    val dLng = Math.toRadians(lng2 - lng1)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(p1) * cos(p2) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * EARTH_MEAN_RADIUS_M * asin(sqrt(h))
}
