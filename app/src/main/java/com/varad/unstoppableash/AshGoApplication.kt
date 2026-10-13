package com.varad.unstoppableash

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class AshGoApplication : Application() {
    /** v2 dependencies; lazy so v1 behaviour is unchanged until v2 code uses it. */
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            // Already enabled
        }
        val db = FirebaseDatabase.getInstance().reference
        db.child("chat").keepSynced(true)
        db.child("buzz").keepSynced(true)
        db.child("tracking").keepSynced(true)
    }
}
