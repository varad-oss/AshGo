package com.varad.unstoppableash.domain.model

/** A shared location point. [accuracyM] null means the device didn't report one. */
data class LocationFix(val lat: Double, val lng: Double, val accuracyM: Float?, val at: Long)
