import re

with open("app/src/main/java/com/varad/unstoppableash/ui/ReceiverScreen.kt", "r") as f:
    content = f.read()

pattern = re.compile(
    r'(verticalAlignment = Alignment\.CenterVertically\s*\{\s*)(OutlinedTextField\()',
    re.MULTILINE
)

bolt_button = r"""\1IconButton(
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
                    \2"""

content = pattern.sub(bolt_button, content)

with open("app/src/main/java/com/varad/unstoppableash/ui/ReceiverScreen.kt", "w") as f:
    f.write(content)

