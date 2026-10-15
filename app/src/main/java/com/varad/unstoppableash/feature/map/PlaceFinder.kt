package com.varad.unstoppableash.feature.map

import com.varad.unstoppableash.data.places.Place
import com.varad.unstoppableash.data.places.PlaceSearch
import com.varad.unstoppableash.data.places.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlaceSearchState {
    data object Idle : PlaceSearchState
    data object Searching : PlaceSearchState
    data class Results(val places: List<Place>) : PlaceSearchState
    data object NoResults : PlaceSearchState
    data object Failed : PlaceSearchState
    data object Unavailable : PlaceSearchState
}

/** Trip destination by name (owner's ask): one search per press; a newer search replaces an older one. */
class PlaceFinder(private val search: PlaceSearch, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<PlaceSearchState>(PlaceSearchState.Idle)
    val state: StateFlow<PlaceSearchState> = _state.asStateFlow()
    private var running: Job? = null

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        running?.cancel()
        _state.value = PlaceSearchState.Searching
        running = scope.launch {
            _state.value = when (val r = search.search(q)) {
                is SearchResult.Found -> if (r.places.isEmpty()) PlaceSearchState.NoResults else PlaceSearchState.Results(r.places)
                SearchResult.Failed -> PlaceSearchState.Failed
                SearchResult.Unavailable -> PlaceSearchState.Unavailable
            }
        }
    }

    fun clear() {
        running?.cancel()
        _state.value = PlaceSearchState.Idle
    }
}
