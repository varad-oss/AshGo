package com.varad.unstoppableash.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SavedPlacesStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun TestScope.store() = SavedPlacesStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.root.resolve("p.preferences_pb") })

    @Test fun savesNamedPlacesAndRemovesThem() = runTest {
        val s = store()
        s.save("Home", 18.52, 73.85)
        s.save("PG", 18.60, 73.80)
        assertEquals(listOf("Home", "PG"), s.places.first().map { it.name })
        s.remove(s.places.first().first { it.name == "PG" }.id)
        assertEquals(listOf("Home"), s.places.first().map { it.name })
    }

    @Test fun savingTheSameNameAgainMovesIt() = runTest {
        val s = store()
        s.save("Home", 1.0, 2.0)
        s.save(" home ", 3.0, 4.0)
        val p = s.places.first().single()
        assertEquals("home", p.name); assertEquals(3.0, p.lat, 0.0)
    }

    @Test fun namesFitTheTripRulesAndBlankNamesAreRefused() = runTest {
        val s = store()
        s.save("   ", 1.0, 2.0)
        s.save("x".repeat(100), 1.0, 2.0)
        assertEquals(listOf(SavedPlacesStore.NAME_MAX), s.places.first().map { it.name.length })
    }
}
