import re

with open("app/src/main/java/com/varad/unstoppableash/ui/TravelerChatScreen.kt", "r") as f:
    content = f.read()

# Add showSelfiePrompt state
if "var showSelfiePrompt" not in content:
    content = content.replace("var messageText by remember { mutableStateOf(\"\") }", "var messageText by remember { mutableStateOf(\"\") }\n    var showSelfiePrompt by remember { mutableStateOf(true) }")

# Add the import for res
if "import com.varad.unstoppableash.R" not in content:
    content = "import com.varad.unstoppableash.R\n" + content
    
if "import androidx.compose.ui.res.painterResource" not in content:
    content = "import androidx.compose.ui.res.painterResource\n" + content

# Replace Icon(Icons.Rounded.CameraAlt...) with Icon(painterResource(R.drawable.ic_snapchat)...)
content = re.sub(r'Icon\(Icons\.Rounded\.CameraAlt.*?\)', 'Icon(painterResource(id = R.drawable.ic_snapchat), contentDescription = "Snapchat", tint = Color.White, modifier = Modifier.size(24.dp))', content)

# Re-insert the popup
popup_code = """
        // Cute Selfie Prompt
        if (showSelfiePrompt) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                color = PremiumAccent.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, PremiumAccent.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { cameraLauncher.launch(null) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(PremiumAccent, CircleShape)
                    ) {
                        Icon(painterResource(id = R.drawable.ic_snapchat), contentDescription = "Camera", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Tap to send a cute snap!",
                        fontWeight = FontWeight.SemiBold,
                        color = PremiumAccent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { cameraLauncher.launch(null) }
                    )
                    IconButton(onClick = { showSelfiePrompt = false }) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.Close, contentDescription = "Close", tint = PremiumAccent)
                    }
                }
            }
        }

        // Input Area
"""

if "// Cute Selfie Prompt" not in content:
    content = content.replace("        // Input Area\n", popup_code)

with open("app/src/main/java/com/varad/unstoppableash/ui/TravelerChatScreen.kt", "w") as f:
    f.write(content)
