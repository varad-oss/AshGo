package com.varad.unstoppableash

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ReceiverService : Service() {
    private val NOTIFICATION_ID = 2
    private val CHANNEL_ID = "receiver_channel"
    private val database = FirebaseDatabase.getInstance().reference
    
    private var buzzListener: ValueEventListener? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        FirebaseDatabase.getInstance().goOnline()

        buzzListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val timestamp = snapshot.getValue(Long::class.java) ?: 0L
                if (System.currentTimeMillis() - timestamp < 10000) {
                    SoundUtil.playShockSound(this@ReceiverService)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.child("buzz").child("receiver").addValueEventListener(buzzListener!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Guardian Mode Active")
            .setContentText("Listening for Ash's SOS and location...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
            
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Guardian Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        buzzListener?.let {
            database.child("buzz").child("receiver").removeEventListener(it)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
