package com.varad.unstoppableash.feature.safety

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.varad.unstoppableash.AshGoApplication
import com.varad.unstoppableash.DevBackend
import com.varad.unstoppableash.V2Activity
import com.varad.unstoppableash.ui.nav.SafetyTab
import com.varad.unstoppableash.ui.nav.safetyPermissions
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.ThemeMode

/**
 * The SOS hold screen for entry points that only deliver taps — the Quick Settings tile now, the home-screen
 * widget at cutover (D-6, D-8). It shows over the lock screen; sending still needs the full 3 s hold.
 */
class SosHoldActivity : ComponentActivity() {
    /**
     * Raising is open to anyone holding the phone; calling it off is not. Over the keyguard, cancel and the
     * false-alarm text wait for the owner's unlock (fingerprint/face keeps that quick).
     */
    private fun afterUnlock(action: () -> Unit) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isKeyguardLocked) return action()
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = action()
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val container = (application as AshGoApplication).container
        DevBackend.apply(this, container)
        setContent {
            OrbitTheme(ThemeMode.SYSTEM) {
                Surface(color = Orbit.colors.background) {
                    val vm: SafetyViewModel = viewModel(factory = viewModelFactory {
                        initializer { SafetyViewModel(container.pairs, container.contacts, container.settings, container.sosController, permissions = { safetyPermissions(applicationContext) }) }
                    })
                    // Managing contacts happens in the app proper (and needs the phone unlocked).
                    SafetyTab(vm, onOpenContacts = { startActivity(Intent(this, V2Activity::class.java)) }, Modifier.safeDrawingPadding(), requireUnlock = ::afterUnlock)
                }
            }
        }
    }
}
