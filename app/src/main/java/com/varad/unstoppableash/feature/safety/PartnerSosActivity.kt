package com.varad.unstoppableash.feature.safety

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.varad.unstoppableash.AshGoApplication
import com.varad.unstoppableash.DevBackend
import com.varad.unstoppableash.data.local.Settings
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.ThemeMode

/** The partner's SOS over the lock screen, opened by the full-screen notification (J10 step 4). */
class PartnerSosActivity : ComponentActivity() {
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
                val vm: PartnerSosViewModel = viewModel(factory = viewModelFactory { initializer { PartnerSosViewModel(container.pairs, container.sos) } })
                val state by vm.state.collectAsStateWithLifecycle()
                val settings by container.settings.settings.collectAsStateWithLifecycle(Settings())
                val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
                LaunchedEffect(lifecycleState) { vm.setVisible(lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) }
                DisposableEffect(vm) { onDispose { vm.setVisible(false) } }
                PartnerSosScreen(
                    state, settings.partnerPhone, container.clock.now(),
                    PartnerSosActions(
                        onAcknowledge = vm::acknowledge,
                        onCall = { dial(it) },
                        onOpenMap = { lat, lng -> runCatching { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, "geo:$lat,$lng?q=$lat,$lng".toUri())) } },
                        onClose = { finish() },
                    ),
                    Modifier.safeDrawingPadding(),
                )
            }
        }
    }

    private fun dial(number: String) {
        runCatching { startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.fromParts("tel", number, null))) }
    }
}
