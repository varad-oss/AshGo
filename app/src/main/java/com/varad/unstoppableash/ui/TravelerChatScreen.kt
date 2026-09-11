package com.varad.unstoppableash.ui
import androidx.compose.ui.res.painterResource
import com.varad.unstoppableash.R


import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.varad.unstoppableash.ui.theme.*
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelerChatScreen(onBack: () -> Unit) {
    var messageText by remember { mutableStateOf("") }
    var showSelfiePrompt by remember { mutableStateOf(true) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            val imageBytes = baos.toByteArray()
            val base64String = Base64.encodeToString(imageBytes, Base64.DEFAULT)
            
            val database = FirebaseDatabase.getInstance().reference
            val msgData = mapOf(
                "text" to "Here's a cute selfie! 📸",
                "isFromTraveler" to true,
                "timestamp" to System.currentTimeMillis(),
                "imageBase64" to base64String
            )
            database.child("chat").push().setValue(msgData)
        }
    }

    LaunchedEffect(Unit) {
        val database = FirebaseDatabase.getInstance().reference
        database.child("chat").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newMessages = mutableListOf<ChatMessage>()
                for (child in snapshot.children) {
                    val text = child.child("text").getValue(String::class.java) ?: ""
                    val isFromTraveler = child.child("isFromTraveler").getValue(Boolean::class.java) ?: false
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val imageBase64 = child.child("imageBase64").getValue(String::class.java)
                    newMessages.add(ChatMessage(text, isFromTraveler, timestamp, imageBase64))
                }
                messages = newMessages
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumBackground)
    ) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PremiumSurface,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp).padding(top = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = PremiumAccent)
                ) {
                    Text("Back", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Guardian Chat",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message = message, isTravelerContext = true)
            }
        }

        

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
                        text = "Make Varad happy with a cute selfie!",
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { cameraLauncher.launch(null) },
                modifier = Modifier
                    .size(48.dp)
                    .background(PremiumSurface, CircleShape)
            ) {
                Icon(painterResource(id = R.drawable.ic_snapchat), contentDescription = "Snapchat", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                placeholder = { Text("Send a message...", color = TextSecondary) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PremiumAccent,
                    unfocusedBorderColor = PremiumSurface,
                    focusedContainerColor = PremiumSurface,
                    unfocusedContainerColor = PremiumSurface,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        val database = FirebaseDatabase.getInstance().reference
                        val msgData = mapOf(
                            "text" to messageText,
                            "isFromTraveler" to true,
                            "timestamp" to System.currentTimeMillis()
                        )
                        database.child("chat").push().setValue(msgData)
                        messageText = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(PremiumAccent, CircleShape)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Rounded.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}
