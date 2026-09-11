package com.varad.unstoppableash

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase

class TrackingService : Service() {

    private val CHANNEL_ID = "AshGoTrackingChannel"
    private val NOTIFICATION_ID = 1
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        setupLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "ACTION_SOS") {
            triggerSOS()
        } else if (action == "ACTION_STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        
        startForeground(NOTIFICATION_ID, createNotification())
        requestLocationUpdates()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ash Go Tracking",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Keeps location tracking active and provides SOS button"
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val sosIntent = Intent(this, TrackingService::class.java).apply {
            action = "ACTION_SOS"
        }
        val sosPendingIntent = PendingIntent.getService(
            this, 0, sosIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ash Go: Active Protection")
            .setContentText("Location is being shared securely.")
            .setSmallIcon(android.R.drawable.ic_secure) // Default secure icon
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Shows on lock screen!
            .addAction(android.R.drawable.ic_dialog_alert, "🚨 TRIGGER SOS 🚨", sosPendingIntent)
            .build()
    }

    private fun setupLocationUpdates() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    // Upload to Firebase
                    val locationData = mapOf(
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "timestamp" to System.currentTimeMillis()
                    )
                    database.child("tracking").child("current_trip").setValue(locationData)
                        .addOnFailureListener { e ->
                            Log.e("TrackingService", "Failed to update location", e)
                        }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun triggerSOS() {
        // Update Firebase that SOS is triggered!
        val alertData = mapOf(
            "isSosActive" to true,
            "timestamp" to System.currentTimeMillis()
        )
        database.child("alerts").push().setValue(alertData)
        Log.i("TrackingService", "SOS TRIGGERED FROM LOCK SCREEN!")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't need bound service, just started service
    }
}
