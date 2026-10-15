package com.varad.unstoppableash.feature.fakecall

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.varad.unstoppableash.data.local.LenientJson
import com.varad.unstoppableash.data.local.safeData
import com.varad.unstoppableash.domain.model.FakeCallVoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Fake-call voices live only on this phone: each picked recording is copied into app storage ([copyIn]) so it
 * keeps working after the picker's permission lapses, and it is never uploaded.
 */
class FakeCallVoiceStore(
    private val store: DataStore<Preferences>,
    private val copyIn: suspend (source: String, id: String) -> String?,
    private val deleteFile: suspend (uri: String) -> Unit,
) {
    val voices: Flow<List<FakeCallVoice>> = store.data.safeData().map { decode(it[KEY]).orEmpty() }

    /** False when the recording can't be read. */
    suspend fun add(name: String, source: String): Boolean {
        val id = UUID.randomUUID().toString()
        val local = copyIn(source, id) ?: return false
        store.edit { prefs ->
            val current = decode(prefs[KEY]) ?: return@edit
            prefs[KEY] = encode(current + FakeCallVoice(id, name.trim().ifEmpty { DEFAULT_NAME }, local))
        }
        return true
    }

    suspend fun remove(id: String) {
        var removed: FakeCallVoice? = null
        store.edit { prefs ->
            val current = decode(prefs[KEY]) ?: return@edit
            removed = current.firstOrNull { it.id == id }
            prefs[KEY] = encode(current.filterNot { it.id == id })
        }
        removed?.let { deleteFile(it.uri) }
    }

    /** v1 voices, copied in once; any whose recording is no longer readable are skipped. */
    suspend fun importLegacyOnce(legacy: () -> List<FakeCallVoice>) {
        val already = store.data.safeData().map { it[IMPORTED] == true }
        if (already.first()) return
        val copied = legacy().mapNotNull { v -> copyIn(v.uri, v.id)?.let { v.copy(uri = it) } }
        store.edit { prefs ->
            if (prefs[IMPORTED] == true) return@edit
            val current = decode(prefs[KEY]) ?: return@edit
            prefs[KEY] = encode((current + copied).distinctBy { it.id })
            prefs[IMPORTED] = true
        }
    }

    @Serializable private data class Dto(val id: String, val name: String, val uri: String)

    private fun decode(json: String?): List<FakeCallVoice>? {
        if (json == null) return emptyList()
        return runCatching { LenientJson.decodeFromString<List<Dto>>(json) }.getOrNull()?.map { FakeCallVoice(it.id, it.name, it.uri) }
    }

    private fun encode(list: List<FakeCallVoice>) = LenientJson.encodeToString(list.map { Dto(it.id, it.name, it.uri) })

    private companion object {
        val KEY = stringPreferencesKey("voices")
        val IMPORTED = booleanPreferencesKey("imported_v1")
        const val DEFAULT_NAME = "Voice"
    }
}
