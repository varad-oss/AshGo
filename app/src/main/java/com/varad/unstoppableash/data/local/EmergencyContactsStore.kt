package com.varad.unstoppableash.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.varad.unstoppableash.domain.model.EmergencyContact
import com.varad.unstoppableash.domain.model.normalizedNumber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** Emergency contacts live only on this phone (never in Firebase). One entry per phone number. */
class EmergencyContactsStore(private val store: DataStore<Preferences>) {

    val contacts: Flow<List<EmergencyContact>> = store.data.map { decode(it[KEY]) }

    /** Returns false when that number is already a contact. */
    suspend fun add(name: String, number: String): Boolean {
        var added = false
        store.edit { prefs ->
            val current = decode(prefs[KEY])
            if (current.none { normalizedNumber(it.number) == normalizedNumber(number) }) {
                prefs[KEY] = encode(current + EmergencyContact(UUID.randomUUID().toString(), name.trim().ifEmpty { number }, number.trim()))
                added = true
            }
        }
        return added
    }

    suspend fun remove(id: String) {
        store.edit { prefs -> prefs[KEY] = encode(decode(prefs[KEY]).filterNot { it.id == id }) }
    }

    /** Copies v1 contacts in exactly once, merging with anything already here. */
    suspend fun importLegacyOnce(legacy: () -> List<EmergencyContact>) {
        store.edit { prefs ->
            if (prefs[IMPORTED] == true) return@edit
            val merged = (decode(prefs[KEY]) + legacy()).distinctBy { normalizedNumber(it.number) }
            prefs[KEY] = encode(merged)
            prefs[IMPORTED] = true
        }
    }

    @Serializable private data class Dto(val id: String, val name: String, val number: String)

    private fun decode(json: String?): List<EmergencyContact> =
        json?.let { runCatching { Json.decodeFromString<List<Dto>>(it) }.getOrNull() }.orEmpty()
            .map { EmergencyContact(it.id, it.name, it.number) }

    private fun encode(list: List<EmergencyContact>): String =
        Json.encodeToString(list.map { Dto(it.id, it.name, it.number) })

    private companion object {
        val KEY = stringPreferencesKey("emergency_contacts_json")
        val IMPORTED = booleanPreferencesKey("legacy_contacts_imported")
    }
}
