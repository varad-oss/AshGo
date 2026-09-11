import re

with open("app/src/main/java/com/varad/unstoppableash/ui/ReceiverScreen.kt", "r") as f:
    content = f.read()

# 1. Add listener
listener_code = """
        // Listen to Buzz
        database.child("buzz").child("receiver").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val timestamp = snapshot.getValue(Long::class.java) ?: 0L
                    if (System.currentTimeMillis() - timestamp < 10000) {
                        com.varad.unstoppableash.SoundUtil.playShockSound(context)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Listen to Alerts"""
content = content.replace("        // Listen to Alerts", listener_code)

# 2. Add Thunder button next to chat box
# We need to find the chat input row in ReceiverScreen.kt
input_row = """
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Send a message...") },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PremiumAccent,
                                unfocusedBorderColor = PremiumSurface,
                                focusedContainerColor = PremiumSurface,
                                unfocusedContainerColor = PremiumSurface
                            )
                        )"""

new_input_row = """
                        IconButton(
                            onClick = {
                                FirebaseDatabase.getInstance().reference.child("buzz").child("traveler").setValue(System.currentTimeMillis())
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(PremiumSurface, CircleShape)
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.Bolt, contentDescription = "Buzz Traveler", tint = androidx.compose.ui.graphics.Color.Yellow)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Send a message...") },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PremiumAccent,
                                unfocusedBorderColor = PremiumSurface,
                                focusedContainerColor = PremiumSurface,
                                unfocusedContainerColor = PremiumSurface
                            )
                        )"""
content = content.replace(input_row, new_input_row)

# Add Bolt import if needed
if "import androidx.compose.material.icons.rounded.Bolt" not in content:
    content = content.replace("import androidx.compose.material.icons.rounded.Send", "import androidx.compose.material.icons.rounded.Send\nimport androidx.compose.material.icons.rounded.Bolt")

with open("app/src/main/java/com/varad/unstoppableash/ui/ReceiverScreen.kt", "w") as f:
    f.write(content)
