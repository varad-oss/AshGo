package com.varad.unstoppableash

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.google.firebase.database.FirebaseDatabase

class SosAccessibilityService : AccessibilityService() {

    private var isVolumeDownPressed = false
    private val handler = Handler(Looper.getMainLooper())
    private val sosRunnable = Runnable { triggerSos() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used, but required to override
    }

    override fun onInterrupt() {
        // Not used, but required to override
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (!isVolumeDownPressed) {
                        isVolumeDownPressed = true
                        Log.i("SosAccessibility", "Volume Down pressed, starting 3 second timer")
                        handler.postDelayed(sosRunnable, 3000)
                    }
                }
                KeyEvent.ACTION_UP -> {
                    isVolumeDownPressed = false
                    Log.i("SosAccessibility", "Volume Down released, cancelling timer")
                    handler.removeCallbacks(sosRunnable)
                }
            }
            // Return false so we don't consume the event and break volume control
            return false
        }
        return super.onKeyEvent(event)
    }

    private fun triggerSos() {
        Log.i("SosAccessibility", "3 Seconds reached! TRIGGERING SOS!")
        val database = FirebaseDatabase.getInstance().reference
        val alertData = mapOf(
            "isSosActive" to true,
            "timestamp" to System.currentTimeMillis()
        )
        database.child("alerts").push().setValue(alertData)
        
        // Vibrate to confirm
        val vibrator = getSystemService(android.os.Vibrator::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(500)
        }
    }
}
