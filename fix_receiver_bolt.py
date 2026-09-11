import re

with open("app/src/main/java/com/varad/unstoppableash/ui/ReceiverScreen.kt", "r") as f:
    content = f.read()

# 1. Remove the "View Live Map" button
map_btn_pattern = re.compile(r'if \(viewMode == "chat"\) \{\s*Button\([\s\S]*?Text\("View Live Map", fontWeight = FontWeight\.Bold\)\s*\}\s*\}')
content = map_btn_pattern.sub('', content)


# 2. Add Thunder Button beside the OutlinedTextField
input_pattern = re.compile(
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
content = input_pattern.sub(bolt_button, content)


# 3. Fix the ringing bug in ReceiverScreen
bug_pattern1 = re.compile(
    r'val loadTime = System\.currentTimeMillis\(\)\s*var lastProcessed = 0L\s*database\.child\("buzz"\)\.child\("receiver"\)\.addValueEventListener\(object : ValueEventListener \{\s*override fun onDataChange\(snapshot: DataSnapshot\) \{\s*if \(snapshot\.exists\(\)\) \{\s*val timestamp = snapshot\.getValue\(Long::class\.java\) \?: 0L\s*if \(timestamp > loadTime && timestamp != lastProcessed\) \{\s*lastProcessed = timestamp\s*com\.varad\.unstoppableash\.SoundUtil\.playShockSound\(context\)\s*\}\s*\}\s*\}',
    re.DOTALL
)

new_listener1 = """var isInitialLoad = true
        database.child("buzz").child("receiver").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isInitialLoad) {
                    isInitialLoad = false
                    return
                }
                if (snapshot.exists()) {
                    com.varad.unstoppableash.SoundUtil.playShockSound(context)
                }
            }"""

content = bug_pattern1.sub(new_listener1, content)


with open("app/src/main/java/com/varad/unstoppableash/ui/ReceiverScreen.kt", "w") as f:
    f.write(content)
