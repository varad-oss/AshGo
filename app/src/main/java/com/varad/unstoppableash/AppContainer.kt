package com.varad.unstoppableash

import com.varad.unstoppableash.service.sos.SosController
import com.varad.unstoppableash.service.sos.SmsDispatcher
import com.varad.unstoppableash.service.sos.AndroidSmsDispatcher
import com.varad.unstoppableash.service.location.LocationSource
import com.varad.unstoppableash.service.location.FusedLocationSource
import android.os.BatteryManager
import android.app.Application
import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.core.time.ServerClock
import com.varad.unstoppableash.data.auth.AuthGateway
import com.varad.unstoppableash.data.auth.FirebaseAuthGateway
import com.varad.unstoppableash.data.chat.ChatRepository
import com.varad.unstoppableash.data.events.EventRepository
import com.varad.unstoppableash.data.firebase.FirebaseRtdbClient
import com.varad.unstoppableash.data.firebase.RtdbClient
import com.varad.unstoppableash.data.media.AndroidPhotoEncoder
import com.varad.unstoppableash.data.media.PhotoEncoder
import com.varad.unstoppableash.data.legacy.LegacyLocalMigration
import com.varad.unstoppableash.data.local.ActiveSosStore
import com.varad.unstoppableash.data.local.EmergencyContactsStore
import com.varad.unstoppableash.data.local.SettingsStore
import com.varad.unstoppableash.data.location.LocationRepository
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.presence.PresenceRepository
import com.varad.unstoppableash.data.sos.SosRepository
import com.varad.unstoppableash.data.trips.TripRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// A corrupt file is replaced with empty preferences instead of crashing every read.
private fun resetOnCorruption() = ReplaceFileCorruptionHandler { emptyPreferences() }
private val Context.contactsStore by preferencesDataStore(name = "v2_emergency_contacts", corruptionHandler = resetOnCorruption())
private val Context.settingsStore by preferencesDataStore(name = "v2_settings", corruptionHandler = resetOnCorruption())
private val Context.activeSosStore by preferencesDataStore(name = "v2_active_sos", corruptionHandler = resetOnCorruption())

/**
 * Manual dependency wiring (ARCHITECTURE_PROPOSAL D-13). Everything is lazy so the v1 app, which
 * still runs until the cutover, pays nothing until v2 code asks for a dependency.
 */
class AppContainer(private val app: Application) {
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var emulatorHost: String? = null
    @Volatile private var backendCreated = false

    /** Debug builds only: point v2 at the local Firebase emulators (demo project). Must run before first use. */
    fun useLocalEmulators(host: String = "10.0.2.2") {
        check(!backendCreated) { "Firebase backend already created" }
        emulatorHost = host
    }

    val usesEmulators: Boolean get() = emulatorHost != null

    private val firebaseApp: FirebaseApp by lazy {
        backendCreated = true
        if (emulatorHost == null) FirebaseApp.getInstance()
        else FirebaseApp.getApps(app).firstOrNull { it.name == DEV_APP } ?: FirebaseApp.initializeApp(
            app,
            FirebaseOptions.Builder().setProjectId("demo-ashgo").setApplicationId("1:1:android:1").setApiKey("demo-key")
                .setDatabaseUrl("https://demo-ashgo-default-rtdb.firebaseio.com").build(),
            DEV_APP,
        )
    }

    val rtdb: RtdbClient by lazy {
        FirebaseRtdbClient(FirebaseDatabase.getInstance(firebaseApp).also { db -> emulatorHost?.let { db.useEmulator(it, 9000) } })
    }
    val auth: AuthGateway by lazy {
        FirebaseAuthGateway(FirebaseAuth.getInstance(firebaseApp).also { a -> emulatorHost?.let { a.useEmulator(it, 9099) } })
    }
    val clock: Clock by lazy { ServerClock(rtdb.serverTimeOffset(), appScope) }

    val pairs by lazy { PairRepository(rtdb, auth, clock) }
    val presence by lazy { PresenceRepository(rtdb) }
    val location by lazy { LocationRepository(rtdb) }
    val chat by lazy { ChatRepository(rtdb) }
    val events by lazy { EventRepository(rtdb, clock) }
    val sos by lazy { SosRepository(rtdb, clock) }
    val trips by lazy { TripRepository(rtdb) }
    val photoEncoder: PhotoEncoder by lazy { AndroidPhotoEncoder(app.contentResolver) }
    val smsDispatcher: SmsDispatcher by lazy { AndroidSmsDispatcher(app) }
    val locationSource: LocationSource by lazy { FusedLocationSource(app) }

    /** App-scoped so an SOS outlives every screen; started on first use, restoring any SOS in progress. */
    val sosController: SosController by lazy {
        SosController(
            appScope, pairs, sos, contacts, activeSos, smsDispatcher, locationSource, clock,
            battery = { batteryPercent() }, fallbackName = { auth.current()?.displayName },
        ).also { it.start() }
    }

    private fun batteryPercent(): Int? =
        (app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }

    val contacts by lazy { EmergencyContactsStore(app.contactsStore) }
    val settings by lazy { SettingsStore(app.settingsStore) }
    val activeSos by lazy { ActiveSosStore(app.activeSosStore) }

    /** Copies v1's on-phone emergency contacts into the v2 store, once. */
    suspend fun importLegacyLocalData() {
        val prefs = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        contacts.importLegacyOnce {
            LegacyLocalMigration.contacts(prefs.getString("emergency_contacts_v2", null), prefs.getString("emergency_contacts", null))
        }
    }

    private companion object {
        const val LEGACY_PREFS = "AshGoPrefs"
        const val DEV_APP = "dev-emulators"
    }
}
