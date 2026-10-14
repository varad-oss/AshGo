package com.varad.unstoppableash

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import com.varad.unstoppableash.service.notify.AndroidNotifier
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
    /** Where a notification tap asked to go; consumed once by the app. */
    private val openRequest = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequest.value = intent.getStringExtra(AndroidNotifier.EXTRA_OPEN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) openRequest.value = intent.getStringExtra(AndroidNotifier.EXTRA_OPEN)
        super.onCreate(savedInstanceState)
        val container = (application as AshGoApplication).container
        DevBackend.apply(this, container)
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
                    AshGoApp(container, openRequest.value, onOpenHandled = { openRequest.value = null }) { GoogleSignIn.idToken(this@V2Activity) }
                }
            }
        }
    }

    companion object {
        const val EXTRA_USE_EMULATORS = DevBackend.EXTRA_USE_EMULATORS
    }
}
