import re

with open("app/src/main/java/com/varad/unstoppableash/MainActivity.kt", "r") as f:
    content = f.read()

old_listener = """        val loadTime = System.currentTimeMillis()
        var lastProcessed = 0L
        database.child("buzz").child("traveler").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (snapshot.exists()) {
                    val timestamp = snapshot.getValue(Long::class.java) ?: 0L
                    if (timestamp > loadTime && timestamp != lastProcessed) {
                        lastProcessed = timestamp
                        com.varad.unstoppableash.SoundUtil.playShockSound(context)
                    }
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })"""

new_listener = """        var isInitialLoad = true
        database.child("buzz").child("traveler").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (isInitialLoad) {
                    isInitialLoad = false
                    return
                }
                if (snapshot.exists()) {
                    com.varad.unstoppableash.SoundUtil.playShockSound(context)
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })"""

content = content.replace(old_listener, new_listener)

with open("app/src/main/java/com/varad/unstoppableash/MainActivity.kt", "w") as f:
    f.write(content)
