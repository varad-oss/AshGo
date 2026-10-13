package com.varad.unstoppableash

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.varad.unstoppableash.data.local.Settings
import com.varad.unstoppableash.feature.onboarding.GoogleSignIn
import com.varad.unstoppableash.ui.nav.AshGoApp
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.ThemeMode

/**
 * v2 entry point. Until the Stage 11 cutover it is registered only in the debug manifest; v1's
 * MainActivity stays the release launcher.
 */
class V2Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AshGoApplication).container
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable && intent.getBooleanExtra(EXTRA_USE_EMULATORS, false) && !container.usesEmulators) {
            runCatching { container.useLocalEmulators() }
        }
        enableEdgeToEdge()
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(Settings())
            val mode = runCatching { ThemeMode.valueOf(settings.themeMode) }.getOrDefault(ThemeMode.SYSTEM)
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            OrbitTheme(mode) {
                Surface(color = Orbit.colors.background) {
                    AshGoApp(container) { GoogleSignIn.idToken(this@V2Activity) }
                }
            }
        }
    }

    companion object {
        const val EXTRA_USE_EMULATORS = "useEmulators"
    }
}
