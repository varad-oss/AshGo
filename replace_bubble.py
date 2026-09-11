import re

with open("app/src/main/java/com/varad/unstoppableash/ui/ReceiverScreen.kt", "r") as f:
    content = f.read()

new_bubble = """fun ChatBubble(message: ChatMessage, isTravelerContext: Boolean = false) {
    // If traveler context, 'me' is traveler. If receiver context, 'me' is receiver.
    val isMe = if (isTravelerContext) message.isFromTraveler else !message.isFromTraveler
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    
    // Decode bitmap outside try-catch of composable
    var decodedBitmap: android.graphics.Bitmap? = null
    if (!message.imageBase64.isNullOrBlank()) {
        try {
            val imageBytes = android.util.Base64.decode(message.imageBase64, android.util.Base64.DEFAULT)
            decodedBitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (isMe) 20.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 20.dp
                ),
                color = if (isMe) PremiumAccent else PremiumSurface,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (decodedBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = androidx.compose.ui.graphics.asImageBitmap(decodedBitmap),
                            contentDescription = "Selfie",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .padding(bottom = if (message.text.isNotBlank()) 8.dp else 0.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    }
                }
            }
            Text(
                text = timeFormat.format(Date(message.timestamp)),
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}"""

# Use regex to find the ChatBubble function and replace it
pattern = re.compile(r"fun ChatBubble.*?^}", re.MULTILINE | re.DOTALL)
new_content = pattern.sub(new_bubble, content)

# Add the import if not present
if "import androidx.compose.ui.graphics.asImageBitmap" not in new_content:
    new_content = "import androidx.compose.ui.graphics.asImageBitmap\n" + new_content

with open("app/src/main/java/com/varad/unstoppableash/ui/ReceiverScreen.kt", "w") as f:
    f.write(new_content)
