package com.varad.unstoppableash.ui.nav

import androidx.activity.compose.BackHandler
import com.varad.unstoppableash.feature.safety.SafetyViewModel
import com.varad.unstoppableash.feature.safety.SafetyScreen
import com.varad.unstoppableash.feature.safety.SafetyPermissions
import com.varad.unstoppableash.feature.safety.SafetyActions
import com.varad.unstoppableash.feature.safety.PickPhoneNumber
import com.varad.unstoppableash.feature.safety.PartnerSosViewModel
import com.varad.unstoppableash.feature.safety.PartnerSosScreen
import com.varad.unstoppableash.feature.safety.PartnerSosActions
import com.varad.unstoppableash.feature.safety.LocationAccess
import com.varad.unstoppableash.feature.safety.ContactsViewModel
import com.varad.unstoppableash.feature.safety.ContactsScreen
import com.varad.unstoppableash.feature.safety.ContactsActions
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import android.content.pm.PackageManager
import android.content.Intent
import android.content.Context
import android.Manifest
import com.varad.unstoppableash.feature.chat.ChatViewModel
import com.varad.unstoppableash.feature.chat.ChatScreen
import com.varad.unstoppableash.feature.chat.ChatActions
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
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
                onCancelOrbit = onboarding::cancelOrbit,
                onSignOut = onboarding::signOut,
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
            HomeViewModel(container.pairs, container.presence, container.location, container.chat, container.events, container.sos, container.trips, container.rtdb, container.clock, container.sosController.state)
        }
    })
    val settingsVm: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(container.pairs, container.presence, container.auth, container.settings) }
    })
    val chatVm: ChatViewModel = viewModel(factory = viewModelFactory {
        initializer { ChatViewModel(container.pairs, container.chat, container.events, container.presence, container.photoEncoder, container.rtdb, container.clock) }
    })
    val context = LocalContext.current
    val safetyVm: SafetyViewModel = viewModel(factory = viewModelFactory {
        initializer { SafetyViewModel(container.pairs, container.contacts, container.settings, container.sosController, permissions = { safetyPermissions(context.applicationContext) }) }
    })
    val homeState by home.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showContacts by rememberSaveable { mutableStateOf(false) }
    var showPartnerSos by rememberSaveable { mutableStateOf(false) }

    if (showContacts) {
        BackHandler { showContacts = false }
        ContactsOverlay(container, onBack = { showContacts = false })
        return
    }
    if (showPartnerSos) {
        BackHandler { showPartnerSos = false }
        PartnerSosOverlay(container, safetyVm, onClose = { showPartnerSos = false })
        return
    }

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
                    onOpenPartnerSos = { showPartnerSos = true },
                    onCheckIn = home::checkIn,
                    onNudge = home::nudge,
                    onDismissError = home::dismissError,
                ),
                padding, // the scaffold's padding already includes the system bars
            )
            Tab.CHAT -> ChatTab(chatVm, padding)
            Tab.SAFETY -> SafetyTab(safetyVm, onOpenContacts = { showContacts = true }, padding)
            Tab.MAP -> ComingLater(padding)
        }
    }
}

@Composable
private fun ChatTab(vm: ChatViewModel, modifier: Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    // "Read" means on screen: this tab is showing and the app is in the foreground.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycleState) { vm.setVisible(lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(vm) { onDispose { vm.setVisible(false) } }
    // The system photo picker needs no storage permission and grants access to the one photo chosen.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.sendPhoto(it.toString()) } }
    ChatScreen(
        state,
        ChatActions(
            onSend = vm::send,
            onRetry = vm::retry,
            onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onNudge = vm::nudge,
            onLoadOlder = vm::loadOlder,
            onClearNotice = vm::clearNotice,
            onDismissError = vm::dismissError,
            photo = vm::photo,
        ),
        modifier,
    )
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

internal fun safetyPermissions(context: Context) = SafetyPermissions(
    sms = granted(context, Manifest.permission.SEND_SMS),
    location = when {
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) -> LocationAccess.PRECISE
        granted(context, Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationAccess.APPROXIMATE
        else -> LocationAccess.OFF
    },
)

/** The dialer, never a direct call: AshGo has no CALL_PHONE and never places calls itself (J9). */
private fun dial(context: Context, number: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))) }
}

@Composable
internal fun SafetyTab(vm: SafetyViewModel, onOpenContacts: () -> Unit, modifier: Modifier, requireUnlock: ((() -> Unit) -> Unit)? = null) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    // Permissions can change in Android settings while we're away; re-read them whenever we come back.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycleState) { if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) vm.refreshPermissions() }
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPermissions() }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshPermissions() }
    val pickPartner = rememberLauncherForActivityResult(PickPhoneNumber()) { picked -> picked?.let { vm.setPartnerPhone(it.number) } }
    SafetyScreen(
        state,
        SafetyActions(
            onRaise = vm::raise,
            onCancel = vm::cancel,
            onRetrySms = vm::retrySms,
            onOfferFalseAlarm = vm::offerFalseAlarm,
            onConfirmFalseAlarm = vm::confirmFalseAlarm,
            onDeclineFalseAlarm = vm::declineFalseAlarm,
            onDone = vm::done,
            onCall = { dial(context, it) },
            onOpenContacts = onOpenContacts,
            onPickPartnerNumber = { pickPartner.launch(Unit) },
            onRequestSms = { askSms.launch(Manifest.permission.SEND_SMS) },
            onRequestLocation = { askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            requireUnlock = requireUnlock,
        ),
        modifier,
    )
}

@Composable
private fun ContactsOverlay(container: AppContainer, onBack: () -> Unit) {
    val vm: ContactsViewModel = viewModel(factory = viewModelFactory { initializer { ContactsViewModel(container.contacts) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(PickPhoneNumber()) { picked -> picked?.let { vm.add(it.name, it.number) } }
    ContactsScreen(
        state,
        ContactsActions(onBack = onBack, onPick = { pick.launch(Unit) }, onAdd = vm::add, onRemove = vm::remove),
        Modifier.safeDrawingPadding(),
    )
}

@Composable
private fun PartnerSosOverlay(container: AppContainer, safety: SafetyViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val vm: PartnerSosViewModel = viewModel(factory = viewModelFactory { initializer { PartnerSosViewModel(container.pairs, container.sos) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val safetyState by safety.state.collectAsStateWithLifecycle()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycleState) { vm.setVisible(lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(vm) { onDispose { vm.setVisible(false) } }
    PartnerSosScreen(
        state, safetyState.partnerPhone, container.clock.now(),
        PartnerSosActions(
            onAcknowledge = vm::acknowledge,
            onCall = { dial(context, it) },
            onOpenMap = { lat, lng -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "geo:$lat,$lng?q=$lat,$lng".toUri())) } },
            onClose = onClose,
        ),
        Modifier.safeDrawingPadding(),
    )
}
