package com.varad.unstoppableash

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class AshGoApplication : Application() {
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
