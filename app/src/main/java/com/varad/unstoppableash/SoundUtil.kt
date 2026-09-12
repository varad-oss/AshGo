package com.varad.unstoppableash

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

object SoundUtil {
    fun playShockSound(context: Context) {
        android.util.Log.i("SoundUtil", "playShockSound called!")
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100) // 100 = max volume
            toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 2000) // Play for 2 seconds
        } catch (e: Exception) {
            android.util.Log.e("SoundUtil", "Failed to play sound: ${e.message}")
            e.printStackTrace()
        }
    }

    fun playSuccessSound(context: Context) {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 1000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
