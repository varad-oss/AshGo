package com.varad.unstoppableash.feature.ourpage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OurPageState(val photos: List<String> = emptyList(), val adding: Boolean = false, val skipped: Int = 0) {
    val full: Boolean get() = photos.size >= OurPhotosStore.MAX
}

class OurPageViewModel(private val store: OurPhotosStore, private val io: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private val _state = MutableStateFlow(OurPageState())
    val state: StateFlow<OurPageState> = _state.asStateFlow()

    init { refresh() }

    fun add(uris: List<String>) {
        if (uris.isEmpty()) return
        _state.update { it.copy(adding = true, skipped = 0) }
        viewModelScope.launch {
            val added = withContext(io) { store.add(uris) }
            _state.update { it.copy(adding = false, skipped = uris.size - added) }
            refresh()
        }
    }

    fun remove(path: String) = viewModelScope.launch {
        withContext(io) { store.remove(java.io.File(path).name) }
        refresh()
    }

    private fun refresh() = viewModelScope.launch {
        val list = withContext(io) { store.list().map { it.path } }
        _state.update { it.copy(photos = list) }
    }
}
