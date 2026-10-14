package com.varad.unstoppableash.ui.nav

import androidx.activity.compose.BackHandler
import com.varad.unstoppableash.service.cleanup.CleanupWorker
import com.varad.unstoppableash.domain.location.SharingPlan
import com.varad.unstoppableash.feature.settings.readReliability
import com.varad.unstoppableash.feature.settings.openFix
import com.varad.unstoppableash.feature.settings.ReliabilityViewModel
import com.varad.unstoppableash.feature.settings.ReliabilityScreen
import com.varad.unstoppableash.feature.settings.ReliabilityActions
import com.varad.unstoppableash.service.connect.HeartbeatWorker
import com.varad.unstoppableash.service.connect.StayConnectedService
import com.varad.unstoppableash.service.notify.Channels
import com.varad.unstoppableash.service.notify.AndroidNotifier
import android.os.Build
import com.varad.unstoppableash.service.location.LocationShareService
import com.varad.unstoppableash.feature.map.OsmMap
import com.varad.unstoppableash.feature.map.MapViewModel
import com.varad.unstoppableash.feature.map.MapScreen
import com.varad.unstoppableash.feature.map.MapActions
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
import com.varad.unstoppableash.feature.settings.SettingsPage
import com.varad.unstoppableash.feature.ourpage.OurPageActions
import com.varad.unstoppableash.feature.ourpage.OurPageScreen
import com.varad.unstoppableash.feature.ourpage.OurPageViewModel
import com.varad.unstoppableash.data.wall.WallRepository
import com.varad.unstoppableash.feature.fakecall.AndroidFakeCallAudio
import com.varad.unstoppableash.feature.fakecall.CallPhase
import com.varad.unstoppableash.feature.fakecall.FakeCallActions
import com.varad.unstoppableash.feature.fakecall.FakeCallScreen
import com.varad.unstoppableash.feature.fakecall.FakeCallViewModel
import android.os.SystemClock
import com.varad.unstoppableash.ui.components.OrbitDialog
import com.varad.unstoppableash.data.places.AndroidPlaceSearch
import androidx.compose.ui.res.stringResource
import com.varad.unstoppableash.R
import com.varad.unstoppableash.feature.settings.PrivacyScreen
import com.varad.unstoppableash.feature.settings.UpdatesScreen
import com.varad.unstoppableash.feature.settings.UpdatesViewModel
import com.varad.unstoppableash.feature.settings.HelpScreen
import com.varad.unstoppableash.feature.settings.AboutScreen
import com.varad.unstoppableash.feature.settings.Fix
import com.varad.unstoppableash.ui.components.LoadingOrbit
import com.varad.unstoppableash.ui.components.LoadingOrbitSize
import kotlinx.coroutines.launch

/** Root: onboarding until the orbit is confirmed, then the four-tab shell. */
@Composable
/**
 * [openRequest] comes from a notification tap ("chat", "map", "safety", "home", "partnerSos"); [onOpenHandled]
 * clears it so rotation doesn't replay it.
 */
fun AshGoApp(
    container: AppContainer,
    openRequest: String? = null,
    onOpenHandled: () -> Unit = {},
    requestGoogleIdToken: suspend () -> Outcome<String>,
) {
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
    MainShell(container, openRequest, onOpenHandled)
}

@Composable
private fun MainShell(container: AppContainer, openRequest: String?, onOpenHandled: () -> Unit) {
    val home: HomeViewModel = viewModel(factory = viewModelFactory {
        initializer {
            HomeViewModel(container.pairs, container.presence, container.location, container.chat, container.events, container.sos, container.trips, container.rtdb, container.clock, container.sosController.state)
        }
    })
    val settingsVm: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(container.pairs, container.presence, container.auth, container.settings, container.avatarEncoder, deleteMyMessages = { container.cleanup.deleteMyMessages() }) }
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
    var showReliability by rememberSaveable { mutableStateOf(false) }
    var showFakeCall by rememberSaveable { mutableStateOf(false) }
    var settingsStartPage by rememberSaveable { mutableStateOf<SettingsPage?>(null) }

    // Every app open: is a newer AshGo published? Quiet unless there is (D-10; owner's ask).
    val updatesVm: UpdatesViewModel = viewModel(factory = viewModelFactory { initializer { UpdatesViewModel(container.updater) } })
    LaunchedEffect(Unit) { updatesVm.checkOnOpen() }
    val updatePrompt by updatesVm.prompt.collectAsStateWithLifecycle()
    updatePrompt?.let { info ->
        OrbitDialog(
            title = stringResource(R.string.update_prompt_title),
            text = stringResource(R.string.update_prompt_body, info.versionName ?: info.versionCode.toString()) + (info.notes?.let { "\n\n$it" } ?: ""),
            confirmLabel = stringResource(R.string.update_prompt_update),
            onConfirm = { updatesVm.download(); settingsStartPage = SettingsPage.UPDATES; showSettings = true },
            dismissLabel = stringResource(R.string.update_prompt_later),
            onDismiss = updatesVm::later,
        )
    }

    if (showFakeCall) {
        FakeCallOverlay(container, onBack = { showFakeCall = false })
        return
    }

    if (showReliability) {
        BackHandler { showReliability = false }
        ReliabilityOverlay(container, onBack = { showReliability = false })
        return
    }

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
        BackHandler { showSettings = false; settingsStartPage = null }
        SettingsOverlay(
            container, settingsVm, onBack = { showSettings = false; settingsStartPage = null },
            onOpenReliability = { showReliability = true }, onOpenContacts = { showContacts = true }, startPage = settingsStartPage,
        )
        return
    }
    LaunchedEffect(openRequest) {
        when (openRequest) {
            AndroidNotifier.OPEN_CHAT -> tab = Tab.CHAT
            AndroidNotifier.OPEN_MAP -> tab = Tab.MAP
            AndroidNotifier.OPEN_SAFETY -> tab = if (homeState.watcher) Tab.HOME else Tab.SAFETY
            AndroidNotifier.OPEN_HOME -> tab = Tab.HOME
            OPEN_PARTNER_SOS -> showPartnerSos = true
        }
        if (openRequest != null) onOpenHandled()
    }
    // Paired: keep the connection that carries alerts (no push server on Spark), and ask once to be allowed to notify.
    LaunchedEffect(Unit) {
        Channels.create(context)
        StayConnectedService.start(context)
        HeartbeatWorker.schedule(context)
        CleanupWorker.schedule(context)
    }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val settingsNow by container.settings.settings.collectAsStateWithLifecycle(null)
    LaunchedEffect(settingsNow?.askedNotifications) {
        val s = settingsNow ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !s.askedNotifications && !granted(context, Manifest.permission.POST_NOTIFICATIONS)) {
            container.settings.update { it.copy(askedNotifications = true) }
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // If I was already sharing (or on a timed pause) when the app opened, make sure the service is running.
    // Restarts it too when it stopped underneath us (sharingActive false), but only if sharing was chosen on this phone.
    val shareHere = settingsNow?.shareFromThisPhone == true
    val serviceUp by LocationShareService.running.collectAsStateWithLifecycle()
    LaunchedEffect(homeState.mySharing, homeState.mySharingUntil, shareHere, serviceUp, homeState.loading) {
        if (!homeState.loading && !serviceUp && SharingPlan.runHere(true, homeState.mySharing, homeState.mySharingUntil, shareHere)) {
            LocationShareService.start(context)
        }
    }
    if (tab != Tab.HOME) BackHandler { tab = Tab.HOME }
    // The watcher has no Safety tab: SOS, contacts and fake call are for the one they watch over.
    val tabs = if (homeState.watcher) Tab.entries - Tab.SAFETY else Tab.entries
    LaunchedEffect(homeState.watcher) { if (homeState.watcher && tab == Tab.SAFETY) tab = Tab.HOME }
    MainScaffold(tab, homeState.unread, onSelect = { tab = it }, tabs = tabs) { padding ->
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
            Tab.SAFETY -> SafetyTab(safetyVm, onOpenContacts = { showContacts = true }, padding, onOpenFakeCall = { showFakeCall = true })
            Tab.MAP -> MapTab(container, onGetHelp = { tab = Tab.SAFETY }, padding)
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

@Composable
private fun MapTab(container: AppContainer, onGetHelp: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val vm: MapViewModel = viewModel(factory = viewModelFactory {
        initializer { MapViewModel(container.pairs, container.presence, container.location, container.trips, container.settings, container.clock, places = AndroidPlaceSearch(context.applicationContext)) }
    })
    val placeSearch by vm.placeSearch.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    // Sharing live needs location permission first; the service only starts once it's granted. Notifications are
    // asked for too: the ongoing "sharing your location" notice is how sharing stays visible (review 8 I4).
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.filterKeys { it != Manifest.permission.POST_NOTIFICATIONS }.values.any { it }) { vm.shareLive(); LocationShareService.start(context) }
    }
    val sharePermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val needsAsk = { !LocationShareService.hasLocationPermission(context) || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(context, Manifest.permission.POST_NOTIFICATIONS)) }
    MapScreen(
        state,
        MapActions(
            onShareLive = {
                if (!needsAsk()) { vm.shareLive(); LocationShareService.start(context) } else askLocation.launch(sharePermissions)
            },
            onPause = vm::pause,
            onStopSharing = vm::stopSharing,
            onStartTrip = { d, e, n, t, share ->
                vm.startTrip(d, e, n, t, share)
                if (share) { if (needsAsk()) askLocation.launch(sharePermissions) else LocationShareService.start(context) }
            },
            onExtendTrip = vm::extendTrip,
            onSearchPlace = vm::searchPlace,
            onClearPlaceSearch = vm::clearPlaceSearch,
            onArrive = vm::arrive,
            onEndTrip = vm::endTrip,
            onGetHelp = onGetHelp,
        ),
        map = { pins, focus, onLongPress, m -> OsmMap(pins, focus, onLongPress, m) },
        modifier = modifier,
        places = placeSearch,
    )
}

const val OPEN_PARTNER_SOS = "partnerSos"

@Composable
private fun SettingsOverlay(
    container: AppContainer, vm: SettingsViewModel, onBack: () -> Unit, onOpenReliability: () -> Unit, onOpenContacts: () -> Unit,
    startPage: SettingsPage? = null,
) {
    val context = LocalContext.current
    val s by vm.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(startPage) }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.setAvatar(it.toString()) } }
    val m = Modifier.safeDrawingPadding()
    page?.let { current ->
        BackHandler { page = null }
        val back = { page = null }
        when (current) {
            SettingsPage.PRIVACY -> PrivacyScreen(s, back, vm::deleteMyMessages, m)
            SettingsPage.UPDATES -> {
                val updates: UpdatesViewModel = viewModel(factory = viewModelFactory { initializer { UpdatesViewModel(container.updater) } })
                val u by updates.state.collectAsStateWithLifecycle()
                UpdatesScreen(u, container.versionName, back, updates::check, updates::download, m)
            }
            SettingsPage.HELP -> HelpScreen(back, m)
            SettingsPage.ABOUT -> AboutScreen(container.versionName, back, m)
            SettingsPage.OUR_PAGE -> {
                val our: OurPageViewModel = viewModel(factory = viewModelFactory { initializer { OurPageViewModel(container.pairs, container.wall, encode = { container.photoEncoder.encode(it) }, cacheDir = container.wallCacheDir) } })
                val o by our.state.collectAsStateWithLifecycle()
                val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(WallRepository.MAX)) { uris -> our.add(uris.map { it.toString() }) }
                OurPageScreen(o, OurPageActions(onBack = back, onAdd = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, onRemove = { our.remove(it) }), m)
            }
            SettingsPage.CONTACTS, SettingsPage.RELIABILITY -> Unit
        }
        return
    }
    SettingsScreen(
        s,
        SettingsActions(
            onBack = onBack,
            onTheme = { vm.setTheme(it) },
            onLeave = vm::leave,
            onSignOut = vm::signOut,
            onDismissFailure = vm::clearFailure,
            onOpen = {
                when (it) {
                    SettingsPage.CONTACTS -> onOpenContacts()
                    SettingsPage.RELIABILITY -> onOpenReliability()
                    else -> page = it
                }
            },
            onRename = vm::rename,
            onPickAvatar = { pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemoveAvatar = vm::removeAvatar,
            onGreeting = vm::setGreeting,
            onNotify = vm::setNotify,
            onOpenSosChannel = { openFix(context, Fix.SOS_CHANNEL_SETTINGS) },
        ),
        m,
    )
}

@Composable
private fun FakeCallOverlay(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: FakeCallViewModel = viewModel(factory = viewModelFactory {
        initializer { FakeCallViewModel(container.fakeCallVoices, AndroidFakeCallAudio(context), SystemClock::elapsedRealtime) }
    })
    val s by vm.state.collectAsStateWithLifecycle()
    BackHandler {
        when (s.phase) {
            CallPhase.Setup -> onBack()
            is CallPhase.Waiting -> vm.cancel()
            CallPhase.Ringing -> vm.decline()
            is CallPhase.InCall -> vm.end()
        }
    }
    val voiceLabel = stringResource(R.string.fake_call_voice)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.addVoice(s.caller.ifBlank { "$voiceLabel ${s.voices.size + 1}" }, it.toString()) }
    }
    FakeCallScreen(
        s,
        FakeCallActions(
            onBack = onBack, onCaller = vm::setCaller, onSelectVoice = vm::selectVoice, onAddVoice = { pick.launch("audio/*") },
            onRemoveVoice = vm::removeVoice, onDelay = vm::setDelay, onStart = { vm.schedule() }, onCancel = vm::cancel,
            onAccept = vm::accept, onDecline = vm::decline, onEnd = vm::end,
        ),
        SystemClock::elapsedRealtime,
        if (s.phase is CallPhase.Setup || s.phase is CallPhase.Waiting) Modifier.safeDrawingPadding() else Modifier,
    )
}

@Composable
private fun ReliabilityOverlay(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: ReliabilityViewModel = viewModel(factory = viewModelFactory {
        initializer { ReliabilityViewModel({ readReliability(context.applicationContext) }, container.rtdb.connected(), StayConnectedService.running) }
    })
    val items by vm.items.collectAsStateWithLifecycle()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycleState) { if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) vm.refresh() }
    ReliabilityScreen(
        items,
        ReliabilityActions(onBack = onBack, onFix = { openFix(context, it) }, onTest = { Channels.create(context); AndroidNotifier(context).postTest() }),
        Modifier.safeDrawingPadding(),
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
internal fun SafetyTab(vm: SafetyViewModel, onOpenContacts: () -> Unit, modifier: Modifier, requireUnlock: ((() -> Unit) -> Unit)? = null, onOpenFakeCall: () -> Unit = {}) {
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
            onOpenFakeCall = onOpenFakeCall,
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
