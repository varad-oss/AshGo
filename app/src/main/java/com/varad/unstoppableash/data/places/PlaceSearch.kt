package com.varad.unstoppableash.data.places

import com.varad.unstoppableash.domain.model.TripDestination

data class Place(val name: String, val detail: String?, val lat: Double, val lng: Double) {
    /** Trip rules allow a 60-character label. */
    fun toDestination() = TripDestination(lat, lng, name.take(60))
}

sealed interface SearchResult {
    data class Found(val places: List<Place>) : SearchResult
    data object Failed : SearchResult
    /** This phone has no place-search service. */
    data object Unavailable : SearchResult
}

/** Finds places by name. Android's is [AndroidPlaceSearch] (the phone's own free geocoder: no key, no cost). */
fun interface PlaceSearch {
    suspend fun search(query: String): SearchResult
}
