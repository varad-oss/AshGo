package com.varad.unstoppableash.core.time

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerClockTest {
    @Test fun startsAtLocalTimeWithZeroOffset() = runTest {
        val clock = ServerClock(MutableStateFlow(0L), backgroundScope) { 1_000L }
        assertEquals(1_000L, clock.now())
    }

    @Test fun offsetErrorDoesNotCrashAndKeepsLastOffset() = runTest(UnconfinedTestDispatcher()) {
        val offsets = kotlinx.coroutines.flow.flow { emit(100L); throw IllegalStateException("listener cancelled") }
        val clock = ServerClock(offsets, backgroundScope) { 1_000L }
        assertEquals(1_100L, clock.now())
    }

    @Test fun appliesLatestOffset() = runTest(UnconfinedTestDispatcher()) {
        val offsets = MutableStateFlow(0L)
        val clock = ServerClock(offsets, backgroundScope) { 1_000L }
        offsets.value = 250L
        assertEquals(1_250L, clock.now())
        offsets.value = -400L
        assertEquals(600L, clock.now())
    }
}
