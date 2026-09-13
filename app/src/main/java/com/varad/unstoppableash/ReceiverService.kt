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
    private var locationListener: ValueEventListener? = null
    private var alertListener: ValueEventListener? = null

    private var lastLocationTime: Long = 0L
    private var hasAlertedDeadSignal = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val signalCheckRunnable = object : Runnable {
        override fun run() {
            if (lastLocationTime != 0L) {
                if (System.currentTimeMillis() - lastLocationTime > 5 * 60 * 1000) {
                    if (!hasAlertedDeadSignal) {
                        hasAlertedDeadSignal = true
                        SoundUtil.playShockSound(this@ReceiverService)
                    showThunderNotification("Connection lost! Last location > 5 mins ago.")
                    }
                } else {
                    hasAlertedDeadSignal = false
                }
            }
            handler.postDelayed(this, 15000)
        }
    }


    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        FirebaseDatabase.getInstance().goOnline()

        buzzListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val timestamp = snapshot.getValue(Long::class.java) ?: 0L
                if (System.currentTimeMillis() - timestamp < 10000) {
                    SoundUtil.playShockSound(this@ReceiverService)
                    showThunderNotification("Aashika wants to talk")
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.child("buzz").child("receiver").addValueEventListener(buzzListener!!)

        locationListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java)
                if (timestamp != null) {
                    lastLocationTime = timestamp
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.child("tracking").child("current_trip").addValueEventListener(locationListener!!)

        alertListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val active = child.child("isSosActive").getValue(Boolean::class.java) ?: false
                    val time = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    if (active && (System.currentTimeMillis() - time) < 15 * 60 * 1000) {
                        // Play shock sound if SOS is active within the last 15 minutes
                        SoundUtil.playShockSound(this@ReceiverService)
                    showThunderNotification("⚠️ EMERGENCY SOS ACTIVATED by Aashika!")
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.child("alerts").orderByChild("timestamp").limitToLast(1).addValueEventListener(alertListener!!)

        handler.post(signalCheckRunnable)

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
        buzzListener?.let {
            database.child("buzz").child("receiver").removeEventListener(it)
        }
        locationListener?.let {
            database.child("tracking").child("current_trip").removeEventListener(it)
        }
        handler.removeCallbacks(signalCheckRunnable)
        alertListener?.let {
            database.child("alerts").removeEventListener(it)
        }


    }


    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        val restartServicePendingIntent = android.app.PendingIntent.getService(
            applicationContext, 1, restartServiceIntent,
            android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val alarmService = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmService.set(android.app.AlarmManager.ELAPSED_REALTIME, android.os.SystemClock.elapsedRealtime() + 1000, restartServicePendingIntent)
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showThunderNotification(message: String) {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        val notification = androidx.core.app.NotificationCompat.Builder(this, "receiver_channel")
            .setContentTitle("Thunder Alert ⚡")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        manager?.notify(System.currentTimeMillis().toInt(), notification)
    }
}