package com.varad.unstoppableash.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.varad.unstoppableash.domain.model.BackendStatus
import com.varad.unstoppableash.domain.model.EmergencyContact
import com.varad.unstoppableash.domain.model.SmsReceipt
import com.varad.unstoppableash.domain.model.SmsStatus
import com.varad.unstoppableash.domain.model.SosState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalStoresTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.store(name: String) =
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmp.root.resolve("$name.preferences_pb") })

    @Test fun contactsRoundTripAndIgnoreDuplicateNumbers() = runTest {
        val s = EmergencyContactsStore(store("contacts"))
        s.add("Mum", "+91 98765 43210")
        s.add("Mum dup", "+919876543210")
        s.add("Priya", "98765-00000")
        val list = s.contacts.first()
        assertEquals(listOf("Mum", "Priya"), list.map { it.name })
        s.remove(list.first().id)
        assertEquals(listOf("Priya"), s.contacts.first().map { it.name })
    }

    @Test fun legacyMigrationRunsOnce() = runTest {
        val s = EmergencyContactsStore(store("contacts2"))
        val legacy = listOf(EmergencyContact("c1", "Mum", "+911"))
        s.importLegacyOnce { legacy }
        s.importLegacyOnce { legacy + EmergencyContact("c2", "Late", "+922") }
        assertEquals(listOf("Mum"), s.contacts.first().map { it.name })
    }

    @Test fun activeSosSurvivesRoundTrip() = runTest {
        val ds = store("sos")
        val s = ActiveSosStore(ds)
        assertEquals(SosState.Idle, s.state.first())
        val active = SosState.Active(
            sosId = "s1", raisedAt = 10L, backend = BackendStatus.SAVED, serverAt = 11L, deliveredToPartnerAt = 12L, partnerAckAt = null,
            sms = mapOf("c1" to SmsReceipt(SmsStatus.DELIVERED), "c2" to SmsReceipt(SmsStatus.FAILED, "no service")),
        )
        s.save(active)
        assertEquals(active, ActiveSosStore(ds).state.first()) // a fresh wrapper re-reads the persisted file
        val cancelled = SosState.Cancelled("s1", 20L, active.sms)
        s.save(cancelled)
        assertEquals(cancelled, s.state.first())
        s.save(SosState.Idle)
        assertEquals(SosState.Idle, s.state.first())
    }

    @Test fun corruptContactsAreNeverOverwritten() = runTest {
        val ds = store("contacts3")
        val key = androidx.datastore.preferences.core.stringPreferencesKey("emergency_contacts_json")
        ds.updateData { it.toMutablePreferences().apply { this[key] = "[{broken" } }
        val s = EmergencyContactsStore(ds)
        assertEquals(false, s.add("Mum", "+911"))
        assertEquals("[{broken", ds.data.first()[key])
    }

    @Test fun activeSosToleratesUnknownFields() = runTest {
        val ds = store("sos2")
        val key = androidx.datastore.preferences.core.stringPreferencesKey("active_sos_json")
        ds.updateData { it.toMutablePreferences().apply { this[key] = """{"kind":"active","sosId":"s9","raisedAt":5,"futureField":true}""" } }
        assertEquals("s9", (ActiveSosStore(ds).state.first() as SosState.Active).sosId)
    }

    @Test fun settingsDefaultsAndUpdates() = runTest {
        val s = SettingsStore(store("settings"))
        assertEquals(Settings(), s.settings.first())
        s.update { it.copy(themeMode = "DARK", greeting = false) }
        assertEquals("DARK", s.settings.first().themeMode)
        s.markNotified("chat", "m42")
        assertEquals("m42", s.settings.first().lastNotified["chat"])
    }
}
