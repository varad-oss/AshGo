package com.varad.unstoppableash.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Per-phone preferences. [lastNotified] holds the newest id already shown per notification kind (dedupe). */
@Serializable
data class Settings(
    val themeMode: String = "SYSTEM",
    val greeting: Boolean = true,
    val notifyChat: Boolean = true,
    val notifyNudges: Boolean = true,
    val notifyCheckins: Boolean = true,
    val notifyPresence: Boolean = true,
    val lastNotified: Map<String, String> = emptyMap(),
)

class SettingsStore(private val store: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<Settings> = store.data.map { decode(it[KEY]) }

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs -> prefs[KEY] = json.encodeToString(transform(decode(prefs[KEY]))) }
    }

    suspend fun markNotified(kind: String, id: String) = update { it.copy(lastNotified = it.lastNotified + (kind to id)) }

    private fun decode(raw: String?): Settings =
        raw?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() } ?: Settings()

    private companion object {
        val KEY = stringPreferencesKey("settings_json")
    }
}
