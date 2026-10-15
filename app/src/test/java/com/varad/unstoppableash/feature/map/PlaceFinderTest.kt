package com.varad.unstoppableash.feature.map

import com.varad.unstoppableash.data.places.Place
import com.varad.unstoppableash.data.places.PlaceSearch
import com.varad.unstoppableash.data.places.SearchResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceFinderTest {
    private val cafe = Place("Blue Tokai, Bandra", "Mumbai, Maharashtra", 19.06, 72.83)

    @Test fun blankQueriesAreNotSent() = runTest {
        var calls = 0
        val f = PlaceFinder(PlaceSearch { calls++; SearchResult.Found(listOf(cafe)) }, backgroundScope)
        f.search("   ")
        testScheduler.runCurrent()
        assertEquals(0, calls)
        assertEquals(PlaceSearchState.Idle, f.state.value)
    }

    @Test fun resultsAreShownAndTheQueryIsTrimmed() = runTest {
        var asked = ""
        val f = PlaceFinder(PlaceSearch { q -> asked = q; SearchResult.Found(listOf(cafe)) }, backgroundScope)
        f.search("  blue tokai ")
        testScheduler.runCurrent()
        assertEquals("blue tokai", asked)
        assertEquals(PlaceSearchState.Results(listOf(cafe)), f.state.value)
    }

    @Test fun nothingFoundAndFailuresAreSaidPlainly() = runTest {
        val empty = PlaceFinder(PlaceSearch { SearchResult.Found(emptyList()) }, backgroundScope)
        empty.search("zzzz"); testScheduler.runCurrent()
        assertEquals(PlaceSearchState.NoResults, empty.state.value)
        val failed = PlaceFinder(PlaceSearch { SearchResult.Failed }, backgroundScope)
        failed.search("cafe"); testScheduler.runCurrent()
        assertEquals(PlaceSearchState.Failed, failed.state.value)
        val none = PlaceFinder(PlaceSearch { SearchResult.Unavailable }, backgroundScope)
        none.search("cafe"); testScheduler.runCurrent()
        assertEquals(PlaceSearchState.Unavailable, none.state.value)
    }

    @Test fun aNewSearchReplacesAnOlderOneStillRunning() = runTest {
        val slow = CompletableDeferred<SearchResult>()
        val f = PlaceFinder(PlaceSearch { q -> if (q == "old") slow.await() else SearchResult.Found(listOf(cafe)) }, backgroundScope)
        f.search("old"); testScheduler.runCurrent()
        assertEquals(PlaceSearchState.Searching, f.state.value)
        f.search("new"); testScheduler.runCurrent()
        slow.complete(SearchResult.Found(emptyList())); testScheduler.runCurrent()
        assertEquals(PlaceSearchState.Results(listOf(cafe)), f.state.value)
    }

    @Test fun aChosenPlaceFitsTheTripRules() {
        val long = Place("x".repeat(100), null, 1.0, 2.0)
        val d = long.toDestination()
        assertTrue(d.label!!.length <= 60)
        assertEquals(1.0, d.lat, 0.0); assertEquals(2.0, d.lng, 0.0)
    }
}
