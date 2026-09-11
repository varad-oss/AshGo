import re

def add_scroll_to_file(filepath):
    with open(filepath, "r") as f:
        content = f.read()

    # 1. Add listState remember
    if "val listState = rememberLazyListState()" not in content:
        content = content.replace("var messages by remember { mutableStateOf(listOf<ChatMessage>()) }",
                                  "var messages by remember { mutableStateOf(listOf<ChatMessage>()) }\n    val listState = androidx.compose.foundation.lazy.rememberLazyListState()")
        content = content.replace("val messages = remember { mutableStateListOf<ChatMessage>() }",
                                  "val messages = remember { mutableStateListOf<ChatMessage>() }\n    val listState = androidx.compose.foundation.lazy.rememberLazyListState()")
    
    # 2. Add LaunchedEffect for auto-scrolling
    auto_scroll_effect = """
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
"""
    if "listState.animateScrollToItem" not in content:
        # insert it before Column(modifier = Modifier.fillMaxSize())
        content = content.replace("Column(", auto_scroll_effect + "\n    Column(", 1)
        
    # 3. Attach listState to LazyColumn
    if "state = listState" not in content:
        content = content.replace("LazyColumn(\n", "LazyColumn(\n            state = listState,\n")

    with open(filepath, "w") as f:
        f.write(content)

add_scroll_to_file("app/src/main/java/com/varad/unstoppableash/ui/TravelerChatScreen.kt")
add_scroll_to_file("app/src/main/java/com/varad/unstoppableash/ui/ReceiverScreen.kt")
