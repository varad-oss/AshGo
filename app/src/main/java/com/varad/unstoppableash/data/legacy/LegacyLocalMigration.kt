package com.varad.unstoppableash.data.legacy

import com.varad.unstoppableash.domain.model.EmergencyContact
import com.varad.unstoppableash.domain.model.FakeCallVoice
import com.varad.unstoppableash.domain.model.normalizedNumber
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class LegacyRole { TRAVELER, RECEIVER }

/**
 * Reads v1's SharedPreferences values ("AshGoPrefs") so local-only data survives the upgrade.
 * Pure parsing: malformed entries are skipped, never thrown.
 */
object LegacyLocalMigration {

    /** v1 keys: `emergency_contacts_v2` (JSON) with `emergency_contacts` (comma-separated numbers) as fallback. */
    fun contacts(v2Json: String?, csv: String?): List<EmergencyContact> {
        val fromJson = objects(v2Json).mapNotNull { o ->
            val number = o.str("number")?.trim()?.ifEmpty { null } ?: return@mapNotNull null
            (o.str("name")?.trim()?.ifEmpty { null } ?: number) to number
        }
        val pairs = fromJson.ifEmpty {
            csv.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { it to it }
        }
        return pairs.distinctBy { normalizedNumber(it.second) }
            .mapIndexed { i, (name, number) -> EmergencyContact("legacy-$i", name, number) }
    }

    /** v1 key `custom_fake_calls`. */
    fun voices(json: String?): List<FakeCallVoice> = objects(json).mapNotNull { o ->
        FakeCallVoice(o.str("id") ?: return@mapNotNull null, o.str("name") ?: return@mapNotNull null, o.str("uri") ?: return@mapNotNull null)
    }

    /** v1 key `user_role`; Stage 10's importer uses it to map `isFromTraveler` to a member. */
    fun role(raw: String?): LegacyRole? = when (raw) {
        "traveler" -> LegacyRole.TRAVELER
        "receiver" -> LegacyRole.RECEIVER
        else -> null
    }

    private fun objects(json: String?): List<JsonObject> {
        if (json.isNullOrBlank()) return emptyList()
        val array = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonArray ?: return emptyList()
        return array.filterIsInstance<JsonObject>()
    }

    private fun JsonObject.str(key: String): String? = runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
}
