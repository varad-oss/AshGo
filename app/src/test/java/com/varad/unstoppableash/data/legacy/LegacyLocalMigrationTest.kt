package com.varad.unstoppableash.data.legacy

import com.varad.unstoppableash.domain.model.FakeCallVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyLocalMigrationTest {
    @Test fun parsesV1ContactJsonSkippingMalformedEntries() {
        val json = """[{"name":"Mum","number":"+91 98765 43210"},{"name":"No number"},42,{"name":"Priya","number":"98765-00000"}]"""
        val contacts = LegacyLocalMigration.contacts(json, csv = null)
        assertEquals(listOf("Mum" to "+91 98765 43210", "Priya" to "98765-00000"), contacts.map { it.name to it.number })
        assertEquals(contacts.map { it.id }.distinct().size, contacts.size)
    }

    @Test fun deduplicatesTheSameNumberWrittenDifferently() {
        val json = """[{"name":"Mum","number":"+91 98765 43210"},{"name":"Mum again","number":"+919876543210"}]"""
        assertEquals(listOf("Mum"), LegacyLocalMigration.contacts(json, null).map { it.name })
    }

    @Test fun fallsBackToCsvWhenJsonMissingOrEmpty() {
        assertEquals(listOf("+911", "+922"), LegacyLocalMigration.contacts(null, " +911 , ,+922").map { it.number })
        assertEquals(listOf("+911"), LegacyLocalMigration.contacts("[]", "+911").map { it.number })
        assertEquals(listOf("+911"), LegacyLocalMigration.contacts("{not json", "+911").map { it.name })
    }

    @Test fun nothingStoredIsEmpty() = assertTrue(LegacyLocalMigration.contacts(null, null).isEmpty())

    @Test fun parsesFakeCallVoices() {
        val json = """[{"id":"v1","name":"Dad","uri":"content://x/1"},{"id":"v2","name":"Bad"}]"""
        assertEquals(listOf(FakeCallVoice("v1", "Dad", "content://x/1")), LegacyLocalMigration.voices(json))
        assertTrue(LegacyLocalMigration.voices("garbage").isEmpty())
    }

    @Test fun mapsLegacyRole() {
        assertEquals(LegacyRole.TRAVELER, LegacyLocalMigration.role("traveler"))
        assertEquals(LegacyRole.RECEIVER, LegacyLocalMigration.role("receiver"))
        assertEquals(null, LegacyLocalMigration.role("x"))
    }
}
