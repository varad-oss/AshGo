package com.varad.unstoppableash.service.connect

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationHostTest {
    @Test fun oneEngineRunsWhileAnyoneNeedsItAndStopsWhenNobodyDoes() = runTest {
        var starts = 0; var running = 0
        val host = NotificationHost(backgroundScope) { starts++; running++; try { awaitCancellation() } finally { running-- } }
        host.acquire(); host.acquire(); testScheduler.runCurrent()
        assertEquals("the app on screen and a service share one engine", 1, starts)
        host.release(); testScheduler.runCurrent()
        assertEquals(1, running)
        host.release(); testScheduler.runCurrent()
        assertEquals("nobody needs it: stopped", 0, running)
        host.acquire(); testScheduler.runCurrent()
        assertEquals(2, starts)
    }

    @Test fun anExtraReleaseNeverGoesNegative() = runTest {
        var running = 0
        val host = NotificationHost(backgroundScope) { running++; try { awaitCancellation() } finally { running-- } }
        host.release(); host.acquire(); testScheduler.runCurrent()
        assertEquals(1, running)
    }
}
