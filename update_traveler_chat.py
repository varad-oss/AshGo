import re

with open("app/src/main/java/com/varad/unstoppableash/ui/TravelerChatScreen.kt", "r") as f:
    content = f.read()

# 1. Remove the showSelfiePrompt block and the state variable
content = re.sub(r'var showSelfiePrompt by remember \{ mutableStateOf\(true\) \}\n?', '', content)

prompt_pattern = re.compile(r'// Cute Selfie Prompt.*?}\n        }\n', re.MULTILINE | re.DOTALL)
content = prompt_pattern.sub('', content)

# 2. Add the Camera button beside the text box
input_row_pattern = re.compile(r'(// Input Area\s*Row\([\s\S]*?verticalAlignment = Alignment\.CenterVertically\s*\)\s*\{)')

camera_button = """
            IconButton(
                onClick = { cameraLauncher.launch(null) },
                modifier = Modifier
                    .size(48.dp)
                    .background(PremiumSurface, CircleShape)
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = "Instant Selfie", tint = PremiumAccent)
            }
            Spacer(modifier = Modifier.width(8.dp))"""

content = input_row_pattern.sub(r'\1' + camera_button, content)

with open("app/src/main/java/com/varad/unstoppableash/ui/TravelerChatScreen.kt", "w") as f:
    f.write(content)

