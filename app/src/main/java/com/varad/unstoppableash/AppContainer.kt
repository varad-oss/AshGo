package com.varad.unstoppableash

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
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

private val Context.contactsStore by preferencesDataStore(name = "v2_emergency_contacts")
private val Context.settingsStore by preferencesDataStore(name = "v2_settings")
private val Context.activeSosStore by preferencesDataStore(name = "v2_active_sos")

/**
 * Manual dependency wiring (ARCHITECTURE_PROPOSAL D-13). Everything is lazy so the v1 app, which
 * still runs until the cutover, pays nothing until v2 code asks for a dependency.
 */
class AppContainer(private val app: Application) {
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val rtdb: RtdbClient by lazy { FirebaseRtdbClient(FirebaseDatabase.getInstance()) }
    val auth: AuthGateway by lazy { FirebaseAuthGateway(FirebaseAuth.getInstance()) }
    val clock: Clock by lazy { ServerClock(rtdb.serverTimeOffset(), appScope) }

    val pairs by lazy { PairRepository(rtdb, auth, clock) }
    val presence by lazy { PresenceRepository(rtdb) }
    val location by lazy { LocationRepository(rtdb) }
    val chat by lazy { ChatRepository(rtdb) }
    val events by lazy { EventRepository(rtdb, clock) }
    val sos by lazy { SosRepository(rtdb) }
    val trips by lazy { TripRepository(rtdb) }

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
    }
}
