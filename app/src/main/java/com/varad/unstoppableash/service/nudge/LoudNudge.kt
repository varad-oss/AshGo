package com.varad.unstoppableash.service.nudge

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

enum class DndFilter { ALL, PRIORITY, ALARMS, NONE }

/** The phone's sound controls, behind an interface so the restore logic is testable. */
interface SoundControl {
    fun alarmVolume(): Int
    fun maxAlarmVolume(): Int
    fun setAlarmVolume(v: Int)
    fun hasDndAccess(): Boolean
    fun dndFilter(): DndFilter
    fun setDndFilter(f: DndFilter)
    /** Plays [BeepTone] on the alarm stream and returns when it has finished. */
    suspend fun playBeep()
}

/**
 * A nudge is meant to be heard (owner's ask): a distinct beep on the alarm stream, which rings through silent and
 * vibrate mode; at full alarm volume; and through Do Not Disturb when the person has granted AshGo DND access
 * (opened for alarms only, for the length of the beep). Whatever happens, the person's own settings come back.
 */
class LoudNudge(private val sound: SoundControl) {
    private val one = Mutex()

    suspend fun ring() = one.withLock {
        val volume = sound.alarmVolume()
        val dnd = sound.dndFilter()
        val openDnd = sound.hasDndAccess() && (dnd == DndFilter.NONE || dnd == DndFilter.PRIORITY)
        try {
            if (openDnd) sound.setDndFilter(DndFilter.ALARMS)
            sound.setAlarmVolume(sound.maxAlarmVolume())
            sound.playBeep()
        } finally {
            withContext(NonCancellable) {
                runCatching { sound.setAlarmVolume(volume) }
                if (openDnd) runCatching { sound.setDndFilter(dnd) }
            }
        }
    }
}

/** AshGo's own nudge sound: three quick two-tone triple beeps (~3 s), unlike any notification sound. Generated, not bundled. */
object BeepTone {
    private const val BEEP_MS = 120
    private const val GAP_MS = 80
    private const val PAUSE_MS = 400
    private const val GROUPS = 3

    fun pcm(sampleRate: Int = 22_050): ShortArray {
        val out = ArrayList<Short>(sampleRate * 3)
        fun silence(ms: Int) = repeat(sampleRate * ms / 1000) { out += 0 }
        fun tone(hz: Double, ms: Int) {
            val n = sampleRate * ms / 1000
            val fade = sampleRate / 200 // 5 ms ramps: no clicks
            for (i in 0 until n) {
                val ramp = min(1.0, min(i, n - 1 - i).toDouble() / fade)
                out += (sin(2 * PI * hz * i / sampleRate) * ramp * 0.9 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        repeat(GROUPS) {
            listOf(1800.0, 2400.0, 1800.0).forEach { hz -> tone(hz, BEEP_MS); silence(GAP_MS) }
            silence(PAUSE_MS)
        }
        return out.toShortArray()
    }
}
