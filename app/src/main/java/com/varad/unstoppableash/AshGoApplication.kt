package com.varad.unstoppableash

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class AshGoApplication : Application() {
    /** App dependencies, created on first use. */
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        DevBackend.applyStored(this) { container } // debug builds only; release never creates the container here
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            // Already enabled
        }
    }
}
