import sys

content = open("app/src/main/java/com/varad/unstoppableash/TrackingService.kt").read()

new_listener = """
    private var chatListener: com.google.firebase.database.ChildEventListener? = null
"""

content = content.replace("private var alertListener: ValueEventListener? = null", "private var alertListener: ValueEventListener? = null\n" + new_listener)

init_chat = """
        chatListener = object : com.google.firebase.database.ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val isFromTraveler = snapshot.child("isFromTraveler").getValue(Boolean::class.java) ?: false
                val text = snapshot.child("text").getValue(String::class.java) ?: ""
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                if (!isFromTraveler && !ChatState.isChatOpen && System.currentTimeMillis() - timestamp < 30000) {
                    showChatNotification("Varad: $text")
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        database.child("chat").addChildEventListener(chatListener!!)
"""

content = content.replace("database.child(\"buzz\").child(\"traveler\").addValueEventListener(buzzListener!!)", "database.child(\"buzz\").child(\"traveler\").addValueEventListener(buzzListener!!)\n" + init_chat)

cleanup_chat = """
        chatListener?.let {
            database.child("chat").removeEventListener(it)
        }
"""

content = content.replace("database.child(\"buzz\").child(\"traveler\").removeEventListener(it)", "database.child(\"buzz\").child(\"traveler\").removeEventListener(it)\n" + cleanup_chat)


show_chat_notif = """
    private fun showChatNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, (System.currentTimeMillis() % 10000).toInt(), intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "tracking_channel")
            .setContentTitle("New Message")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }
"""

content = content.replace("private fun triggerSOS() {", show_chat_notif + "\n    private fun triggerSOS() {")

open("app/src/main/java/com/varad/unstoppableash/TrackingService.kt", "w").write(content)
