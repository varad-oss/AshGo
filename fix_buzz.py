import re

def fix_file(filepath, child_node):
    with open(filepath, "r") as f:
        content = f.read()

    # Find the buzz listener
    pattern = re.compile(
        r'database\.child\("buzz"\)\.child\("' + child_node + r'"\)\.addValueEventListener\(object :.*?override fun onCancelled\(error: .*?\{\}\s*\n\s*\}\)',
        re.DOTALL
    )

    new_listener = f"""val loadTime = System.currentTimeMillis()
        var lastProcessed = 0L
        database.child("buzz").child("{child_node}").addValueEventListener(object : com.google.firebase.database.ValueEventListener {{
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {{
                if (snapshot.exists()) {{
                    val timestamp = snapshot.getValue(Long::class.java) ?: 0L
                    if (timestamp > loadTime && timestamp != lastProcessed) {{
                        lastProcessed = timestamp
                        com.varad.unstoppableash.SoundUtil.playShockSound(context)
                    }}
                }}
            }}
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {{}}
        }})"""

    # In ReceiverScreen, it uses ValueEventListener directly, in MainActivity it uses com.google.firebase.database.ValueEventListener
    if filepath == "app/src/main/java/com/varad/unstoppableash/ui/ReceiverScreen.kt":
        new_listener = new_listener.replace("com.google.firebase.database.", "")

    content = pattern.sub(new_listener, content)

    with open(filepath, "w") as f:
        f.write(content)

fix_file("app/src/main/java/com/varad/unstoppableash/MainActivity.kt", "traveler")
fix_file("app/src/main/java/com/varad/unstoppableash/ui/ReceiverScreen.kt", "receiver")

