package com.varad.unstoppableash.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.util.UUID

data class SavedPlace(val id: String, val name: String, val lat: Double, val lng: Double)

/** "Home", "PG"…: trip destinations saved on this phone only (owner's ask). One per name; a few dozen at most. */
class SavedPlacesStore(private val store: DataStore<Preferences>) {

    val places: Flow<List<SavedPlace>> = store.data.safeData().map { decode(it[KEY]).orEmpty() }

    suspend fun save(name: String, lat: Double, lng: Double) {
        val clean = name.trim().take(NAME_MAX)
        if (clean.isEmpty()) return
        store.edit { prefs ->
            val current = decode(prefs[KEY]) ?: return@edit // never overwrite a file we couldn't read
            val kept = current.filterNot { it.name.equals(clean, ignoreCase = true) }
            prefs[KEY] = encode((kept + SavedPlace(UUID.randomUUID().toString(), clean, lat, lng)).takeLast(MAX))
        }
    }

    suspend fun remove(id: String) {
        store.edit { prefs -> decode(prefs[KEY])?.let { prefs[KEY] = encode(it.filterNot { p -> p.id == id }) } }
    }

    @Serializable private data class Dto(val id: String, val name: String, val lat: Double, val lng: Double)

    private fun decode(json: String?): List<SavedPlace>? {
        if (json == null) return emptyList()
        return runCatching { LenientJson.decodeFromString<List<Dto>>(json) }.getOrNull()?.map { SavedPlace(it.id, it.name, it.lat, it.lng) }
    }

    private fun encode(list: List<SavedPlace>) = LenientJson.encodeToString(list.map { Dto(it.id, it.name, it.lat, it.lng) })

    companion object {
        /** Trip rules allow a 60-character label; place names are kept short. */
        const val NAME_MAX = 30
        const val MAX = 20
        private val KEY = stringPreferencesKey("places")
    }
}
