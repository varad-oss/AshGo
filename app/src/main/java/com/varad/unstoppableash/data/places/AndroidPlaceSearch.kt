package com.varad.unstoppableash.data.places

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Place search through Android's built-in [Geocoder]: free, no API key, nothing billed (₹0). Searches run only when
 * the person presses Search, never per keystroke.
 */
class AndroidPlaceSearch(context: Context) : PlaceSearch {
    private val app = context.applicationContext

    override suspend fun search(query: String): SearchResult {
        if (!Geocoder.isPresent()) return SearchResult.Unavailable
        val geocoder = Geocoder(app)
        val addresses = withTimeoutOrNull(15_000) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine<List<Address>> { cont ->
                        geocoder.getFromLocationName(query, MAX, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)
                            override fun onError(errorMessage: String?) = cont.resume(emptyList())
                        })
                    }
                } else {
                    @Suppress("DEPRECATION")
                    withContext(Dispatchers.IO) { geocoder.getFromLocationName(query, MAX).orEmpty() }
                }
            }.getOrNull()
        } ?: return SearchResult.Failed
        return SearchResult.Found(addresses.filter { it.hasLatitude() && it.hasLongitude() }.map(::toPlace))
    }

    private fun toPlace(a: Address): Place {
        val lines = (0..a.maxAddressLineIndex).mapNotNull { a.getAddressLine(it) }.joinToString(", ")
        // featureName is often just the city or a house number; then the first part of the address says more.
        val generic = setOf(a.locality, a.subAdminArea, a.adminArea, a.countryName).filterNotNull()
        val name = a.featureName?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) && it !in generic }
            ?: lines.substringBefore(",").ifBlank { "${a.latitude}, ${a.longitude}" }
        val detail = lines.removePrefix(name).trimStart(',', ' ').ifBlank { listOfNotNull(a.locality, a.adminArea).joinToString(", ") }.ifBlank { null }
        return Place(name, detail, a.latitude, a.longitude)
    }

    private companion object { const val MAX = 6 }
}
