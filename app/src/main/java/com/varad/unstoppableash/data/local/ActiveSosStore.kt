package com.varad.unstoppableash.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.varad.unstoppableash.domain.model.BackendStatus
import com.varad.unstoppableash.domain.model.SmsReceipt
import com.varad.unstoppableash.domain.model.SmsStatus
import com.varad.unstoppableash.domain.model.SosState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Persists this phone's SOS state so a crash or process death mid-emergency loses nothing. */
class ActiveSosStore(private val store: DataStore<Preferences>) {

    val state: Flow<SosState> = store.data.map { decode(it[KEY]) }

    suspend fun save(state: SosState) {
        store.edit { prefs -> if (state == SosState.Idle) prefs.remove(KEY) else prefs[KEY] = encode(state) }
    }

    @Serializable private data class Receipt(val status: String, val failure: String? = null)

    @Serializable private data class Dto(
        val kind: String,
        val sosId: String,
        val raisedAt: Long = 0,
        val backend: String = BackendStatus.PENDING.name,
        val serverAt: Long? = null,
        val deliveredAt: Long? = null,
        val ackAt: Long? = null,
        val cancelledAt: Long = 0,
        val sms: Map<String, Receipt> = emptyMap(),
    )

    private fun encode(s: SosState): String = Json.encodeToString(
        when (s) {
            is SosState.Active -> Dto("active", s.sosId, s.raisedAt, s.backend.name, s.serverAt, s.deliveredToPartnerAt, s.partnerAckAt, sms = receipts(s.sms))
            is SosState.Cancelled -> Dto("cancelled", s.sosId, cancelledAt = s.cancelledAt, sms = receipts(s.sms))
            SosState.Idle -> error("Idle is stored as absence")
        },
    )

    private fun decode(json: String?): SosState {
        val d = json?.let { runCatching { Json.decodeFromString<Dto>(it) }.getOrNull() } ?: return SosState.Idle
        val sms = d.sms.mapNotNull { (k, v) -> runCatching { k to SmsReceipt(SmsStatus.valueOf(v.status), v.failure) }.getOrNull() }.toMap()
        return when (d.kind) {
            "active" -> SosState.Active(
                d.sosId, d.raisedAt, runCatching { BackendStatus.valueOf(d.backend) }.getOrDefault(BackendStatus.PENDING),
                d.serverAt, d.deliveredAt, d.ackAt, sms,
            )
            "cancelled" -> SosState.Cancelled(d.sosId, d.cancelledAt, sms)
            else -> SosState.Idle
        }
    }

    private fun receipts(sms: Map<String, SmsReceipt>) = sms.mapValues { Receipt(it.value.status.name, it.value.failure) }

    private companion object {
        val KEY = stringPreferencesKey("active_sos_json")
    }
}
