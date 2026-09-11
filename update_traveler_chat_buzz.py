import re

with open("app/src/main/java/com/varad/unstoppableash/ui/TravelerChatScreen.kt", "r") as f:
    content = f.read()

# Add Thunder button next to camera button
camera_button = """            IconButton(
                onClick = { cameraLauncher.launch(null) },
                modifier = Modifier
                    .size(48.dp)
                    .background(PremiumSurface, CircleShape)
            ) {
                Icon(painterResource(id = R.drawable.ic_snapchat), contentDescription = "Snapchat", tint = Color.White, modifier = Modifier.size(24.dp))
            }"""

thunder_button = """            IconButton(
                onClick = { cameraLauncher.launch(null) },
                modifier = Modifier
                    .size(48.dp)
                    .background(PremiumSurface, CircleShape)
            ) {
                Icon(painterResource(id = R.drawable.ic_snapchat), contentDescription = "Snapchat", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { 
                    com.google.firebase.database.FirebaseDatabase.getInstance().reference.child("buzz").child("receiver").setValue(System.currentTimeMillis()) 
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(PremiumSurface, CircleShape)
            ) {
                Icon(androidx.compose.material.icons.Icons.Rounded.Bolt, contentDescription = "Buzz Receiver", tint = androidx.compose.ui.graphics.Color.Yellow)
            }"""
content = content.replace(camera_button, thunder_button)

with open("app/src/main/java/com/varad/unstoppableash/ui/TravelerChatScreen.kt", "w") as f:
    f.write(content)
