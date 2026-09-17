package com.varad.unstoppableash

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Centralised access point for all Firebase Realtime Database operations.
 *
 * Every database path constant lives here so that schema changes only need
 * a single-file edit.  Helper methods wrap common read / write patterns so
 * callers don't need to import Firebase types directly.
 */
object FirebaseRepository {

    // ── Root Reference ──────────────────────────────────────────────────
    val database: DatabaseReference
        get() = FirebaseDatabase.getInstance().reference

    // ── Path Constants ──────────────────────────────────────────────────
    // Tracking
    const val PATH_TRACKING          = "tracking"
    const val PATH_CURRENT_TRIP      = "tracking/current_trip"
    const val PATH_VEHICLE_INFO      = "tracking/vehicle_info"
    const val PATH_DESTINATION       = "tracking/destination"
    const val PATH_ARRIVED           = "tracking/arrived"

    // Chat
    const val PATH_CHAT              = "chat"

    // Alerts / SOS
    const val PATH_ALERTS            = "alerts"

    // Buzz (Thunder)
    const val PATH_BUZZ_TRAVELER     = "buzz/traveler"
    const val PATH_BUZZ_RECEIVER     = "buzz/receiver"

    // Notifications (custom push-style)
    const val PATH_NOTIF_TRAVELER    = "notifications/traveler"
    const val PATH_NOTIF_RECEIVER    = "notifications/receiver"

    // Users
    const val PATH_USERS             = "users"
    const val PATH_USER_TRAVELER     = "users/traveler"
    const val PATH_USER_RECEIVER     = "users/receiver"

    // App config (auto-updater)
    const val PATH_APP_CONFIG        = "app_config"

    // ── Convenience References ──────────────────────────────────────────
    fun chatRef(): DatabaseReference          = database.child(PATH_CHAT)
    fun alertsRef(): DatabaseReference        = database.child(PATH_ALERTS)
    fun currentTripRef(): DatabaseReference   = database.child(PATH_CURRENT_TRIP)
    fun vehicleInfoRef(): DatabaseReference   = database.child(PATH_VEHICLE_INFO)
    fun destinationRef(): DatabaseReference   = database.child(PATH_DESTINATION)
    fun arrivedRef(): DatabaseReference       = database.child(PATH_ARRIVED)
    fun buzzTravelerRef(): DatabaseReference  = database.child(PATH_BUZZ_TRAVELER)
    fun buzzReceiverRef(): DatabaseReference  = database.child(PATH_BUZZ_RECEIVER)
    fun userRef(role: String): DatabaseReference = database.child(PATH_USERS).child(role)
    fun profilePicRef(role: String): DatabaseReference =
        userRef(role).child("profilePicBase64")
    fun displayNameRef(role: String): DatabaseReference =
        userRef(role).child("displayName")
    fun appConfigRef(): DatabaseReference     = database.child(PATH_APP_CONFIG)

    // ── Chat Helpers ────────────────────────────────────────────────────
    fun sendChatMessage(text: String, isFromTraveler: Boolean, imageBase64: String? = null, imageUrl: String? = null) {
        val msgData = mutableMapOf<String, Any>(
            "text" to text,
            "isFromTraveler" to isFromTraveler,
            "timestamp" to System.currentTimeMillis()
        )
        if (imageBase64 != null) msgData["imageBase64"] = imageBase64
        if (imageUrl != null) msgData["imageUrl"] = imageUrl
        chatRef().push().setValue(msgData)
    }

    // ── Alert Helpers ───────────────────────────────────────────────────
    fun triggerSos() {
        val alertData = mapOf(
            "isSosActive" to true,
            "timestamp" to System.currentTimeMillis()
        )
        alertsRef().push().setValue(alertData)
    }

    fun cancelSos() {
        val alertData = mapOf(
            "isSosActive" to false,
            "timestamp" to System.currentTimeMillis()
        )
        alertsRef().push().setValue(alertData)
    }

    // ── Buzz Helpers ────────────────────────────────────────────────────
    fun sendBuzz(targetRole: String) {
        val ref = if (targetRole == "traveler") buzzTravelerRef() else buzzReceiverRef()
        ref.setValue(System.currentTimeMillis())
    }

    // ── Location Helpers ────────────────────────────────────────────────
    fun updateLocation(lat: Double, lng: Double, speed: Float = 0f, batteryPct: Int = -1) {
        val data = mutableMapOf<String, Any>(
            "latitude" to lat,
            "longitude" to lng,
            "timestamp" to System.currentTimeMillis()
        )
        if (speed >= 0) data["speed"] = speed
        if (batteryPct in 0..100) data["batteryPct"] = batteryPct
        currentTripRef().setValue(data)
    }

    // ── Cleanup Helpers ─────────────────────────────────────────────────
    fun cleanOldEntries(path: String, maxAgeMs: Long) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        database.child(path).orderByChild("timestamp").endAt(cutoff.toDouble())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) child.ref.removeValue()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // ── Connection State ────────────────────────────────────────────────
    fun connectedRef(): DatabaseReference =
        FirebaseDatabase.getInstance().getReference(".info/connected")
}