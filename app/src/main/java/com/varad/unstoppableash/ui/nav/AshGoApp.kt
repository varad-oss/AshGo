package com.varad.unstoppableash.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.varad.unstoppableash.AppContainer
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.feature.home.HomeActions
import com.varad.unstoppableash.feature.home.HomeScreen
import com.varad.unstoppableash.feature.home.HomeViewModel
import com.varad.unstoppableash.feature.onboarding.GoogleSignIn
import com.varad.unstoppableash.feature.onboarding.OnboardingActions
import com.varad.unstoppableash.feature.onboarding.OnboardingScreen
import com.varad.unstoppableash.feature.onboarding.OnboardingStep
import com.varad.unstoppableash.feature.onboarding.OnboardingViewModel
import com.varad.unstoppableash.feature.settings.SettingsActions
import com.varad.unstoppableash.feature.settings.SettingsScreen
import com.varad.unstoppableash.feature.settings.SettingsViewModel
import com.varad.unstoppableash.ui.components.LoadingOrbit
import com.varad.unstoppableash.ui.components.LoadingOrbitSize
import kotlinx.coroutines.launch

/** Root: onboarding until the orbit is confirmed, then the four-tab shell. */
@Composable
fun AshGoApp(container: AppContainer, requestGoogleIdToken: suspend () -> Outcome<String>) {
    val onboarding: OnboardingViewModel = viewModel(factory = viewModelFactory { initializer { OnboardingViewModel(container.auth, container.pairs) } })
    val ob by onboarding.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    if (ob.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingOrbit(size = LoadingOrbitSize.LARGE) }
        return
    }
    if (ob.step != OnboardingStep.DONE) {
        OnboardingScreen(
            ob,
            OnboardingActions(
                onSignIn = {
                    scope.launch {
                        when (val r = requestGoogleIdToken()) {
                            is Outcome.Success -> onboarding.signIn(r.value)
                            is Outcome.Failure -> if (r.detail != GoogleSignIn.CANCELLED) onboarding.reportFailure(r.reason)
                        }
                    }
                },
                onDevSignIn = if (container.usesEmulators) {
                    { onboarding.signIn("""{"sub":"dev-alice","email":"alice.dev@example.com","email_verified":true,"name":"Alice"}""") }
                } else null,
                onChooseCreate = onboarding::chooseCreate,
                onChooseJoin = onboarding::chooseJoin,
                onBack = onboarding::back,
                onCreate = onboarding::create,
                onInvite = onboarding::invite,
                onInviteAgain = onboarding::inviteAgain,
                onJoin = onboarding::join,
                onDecline = onboarding::decline,
                onConfirm = onboarding::confirm,
                onNotThem = onboarding::notThem,
                onDismissFailure = onboarding::clearFailure,
            ),
        )
        return
    }
    MainShell(container)
}

@Composable
private fun MainShell(container: AppContainer) {
    val home: HomeViewModel = viewModel(factory = viewModelFactory {
        initializer {
            HomeViewModel(container.pairs, container.presence, container.location, container.chat, container.events, container.sos, container.trips, container.rtdb, container.clock)
        }
    })
    val settingsVm: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(container.pairs, container.presence, container.auth, container.settings) }
    })
    val homeState by home.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    if (showSettings) {
        BackHandler { showSettings = false }
        val s by settingsVm.state.collectAsStateWithLifecycle()
        SettingsScreen(
            s,
            SettingsActions(
                onBack = { showSettings = false },
                onTheme = { settingsVm.setTheme(it) },
                onLeave = settingsVm::leave,
                onSignOut = settingsVm::signOut,
                onDismissFailure = settingsVm::clearFailure,
            ),
            Modifier.safeDrawingPadding(),
        )
        return
    }
    if (tab != Tab.HOME) BackHandler { tab = Tab.HOME }
    MainScaffold(tab, homeState.unread, onSelect = { tab = it }) { padding ->
        when (tab) {
            Tab.HOME -> HomeScreen(
                homeState,
                HomeActions(
                    onOpenSettings = { showSettings = true },
                    onOpenMap = { tab = Tab.MAP },
                    onOpenSafety = { tab = Tab.SAFETY },
                    onCheckIn = home::checkIn,
                    onNudge = home::nudge,
                    onDismissError = home::dismissError,
                ),
                padding, // the scaffold's padding already includes the system bars
            )
            Tab.MAP, Tab.CHAT, Tab.SAFETY -> ComingLater(padding)
        }
    }
}
