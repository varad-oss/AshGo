package com.varad.unstoppableash.service.nudge

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudNudgeTest {
    private class FakeSound(var volume: Int = 2, val max: Int = 7, var filter: DndFilter = DndFilter.ALL, val dndAccess: Boolean = true) : SoundControl {
        val log = mutableListOf<String>()
        var failPlay = false
        override fun alarmVolume() = volume
        override fun maxAlarmVolume() = max
        override fun setAlarmVolume(v: Int) { volume = v; log += "vol:$v" }
        override fun hasDndAccess() = dndAccess
        override fun dndFilter() = filter
        override fun setDndFilter(f: DndFilter) { filter = f; log += "dnd:$f" }
        override suspend fun playBeep() {
            log += "beep(vol=$volume,dnd=$filter)"
            delay(3_000)
            if (failPlay) error("audio device gone")
        }
    }

    @Test fun beepsAtFullVolumeThenRestoresTheVolume() = runTest {
        val s = FakeSound(volume = 2)
        LoudNudge(s).ring()
        assertEquals(listOf("vol:7", "beep(vol=7,dnd=ALL)", "vol:2"), s.log)
    }

    @Test fun doNotDisturbIsOpenedForAlarmsOnlyAndPutBack() = runTest {
        val s = FakeSound(filter = DndFilter.NONE)
        LoudNudge(s).ring()
        assertEquals(listOf("dnd:ALARMS", "vol:7", "beep(vol=7,dnd=ALARMS)", "vol:2", "dnd:NONE"), s.log)
    }

    @Test fun withoutDndAccessItStillBeepsAndLeavesDndAlone() = runTest {
        val s = FakeSound(filter = DndFilter.PRIORITY, dndAccess = false)
        LoudNudge(s).ring()
        assertTrue(s.log.none { it.startsWith("dnd:") })
        assertTrue(s.log.any { it.startsWith("beep") })
    }

    @Test fun settingsComeBackEvenWhenPlaybackFails() = runTest {
        val s = FakeSound(volume = 3, filter = DndFilter.PRIORITY).apply { failPlay = true }
        runCatching { LoudNudge(s).ring() }
        assertEquals(3, s.volume)
        assertEquals(DndFilter.PRIORITY, s.filter)
    }

    @Test fun nudgesArrivingTogetherBeepOneAfterAnotherAndRestoreTheOriginal() = runTest {
        val s = FakeSound(volume = 1)
        val n = LoudNudge(s)
        listOf(async { n.ring() }, async { n.ring() }).awaitAll()
        assertEquals(2, s.log.count { it.startsWith("beep") })
        assertEquals("never 'restores' to the raised volume", 1, s.volume)
    }

    @Test fun theBeepIsAudibleAndBounded() {
        val pcm = BeepTone.pcm(sampleRate = 22_050)
        val seconds = pcm.size / 22_050.0
        assertTrue("about 3 s, got $seconds", seconds in 2.5..3.5)
        assertTrue("loud", pcm.maxOf { it.toInt() } > Short.MAX_VALUE / 2)
        assertTrue("has gaps between beeps", pcm.count { it.toInt() == 0 } > 22_050 / 4)
    }
}
