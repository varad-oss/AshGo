package com.varad.unstoppableash.service.nudge

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.delay

/** [SoundControl] on a real phone: the alarm stream, DND (only with the access the person granted), vibration. */
class AndroidSoundControl(context: Context) : SoundControl {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val nm = app.getSystemService(NotificationManager::class.java)

    override fun alarmVolume() = audio.getStreamVolume(AudioManager.STREAM_ALARM)
    override fun maxAlarmVolume() = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    override fun setAlarmVolume(v: Int) = audio.setStreamVolume(AudioManager.STREAM_ALARM, v, 0)
    override fun hasDndAccess() = nm.isNotificationPolicyAccessGranted

    override fun dndFilter() = when (nm.currentInterruptionFilter) {
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> DndFilter.PRIORITY
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> DndFilter.ALARMS
        NotificationManager.INTERRUPTION_FILTER_NONE -> DndFilter.NONE
        else -> DndFilter.ALL
    }

    override fun setDndFilter(f: DndFilter) = nm.setInterruptionFilter(
        when (f) {
            DndFilter.ALL -> NotificationManager.INTERRUPTION_FILTER_ALL
            DndFilter.PRIORITY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
            DndFilter.ALARMS -> NotificationManager.INTERRUPTION_FILTER_ALARMS
            DndFilter.NONE -> NotificationManager.INTERRUPTION_FILTER_NONE
        },
    )

    override suspend fun playBeep() {
        val pcm = BeepTone.pcm(SAMPLE_RATE)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            runCatching {
                app.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(700, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            delay(pcm.size * 1000L / SAMPLE_RATE + 100)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private companion object { const val SAMPLE_RATE = 22_050 }
}
