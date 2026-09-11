import re

with open("app/src/main/java/com/varad/unstoppableash/MainActivity.kt", "r") as f:
    content = f.read()

listener_code = """
    LaunchedEffect(Unit) {
        val database = com.google.firebase.database.FirebaseDatabase.getInstance().reference
        database.child("buzz").child("traveler").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (snapshot.exists()) {
                    val timestamp = snapshot.getValue(Long::class.java) ?: 0L
                    if (System.currentTimeMillis() - timestamp < 10000) {
                        com.varad.unstoppableash.SoundUtil.playShockSound(context)
                    }
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    // Subtle breathing animation for the SOS button"""

content = content.replace("    // Subtle breathing animation for the SOS button", listener_code)

with open("app/src/main/java/com/varad/unstoppableash/MainActivity.kt", "w") as f:
    f.write(content)
