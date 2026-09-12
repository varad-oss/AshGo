package com.varad.unstoppableash.ui

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage

import com.varad.unstoppableash.ui.theme.PremiumAccent
import com.varad.unstoppableash.ui.theme.PremiumBackground
import com.varad.unstoppableash.ui.theme.PremiumSurface
import com.varad.unstoppableash.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.*

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

data class ChatMessage(
    val text: String, 
    val isFromTraveler: Boolean, 
    val timestamp: Long,
    val imageBase64: String? = null,
    val imageUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiverScreen(initialMode: String = "split") {
    var viewMode by remember { mutableStateOf(initialMode) }
    var messageText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    // Pune coordinates default
    var travelerLocation by remember { mutableStateOf(Pair(18.5204, 73.8567)) }
    var isSosActive by remember { mutableStateOf(false) }
    var vehicleInfo by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    DisposableEffect(Unit) {
        val database = FirebaseDatabase.getInstance().reference
        
        // Listen to Vehicle Info
        val vehicleListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                vehicleInfo = snapshot.getValue(String::class.java) ?: ""
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        val vehicleRef = database.child("tracking").child("vehicle_info")
        vehicleRef.addValueEventListener(vehicleListener)

        // Listen to Location
        val locationListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("latitude").getValue(Double::class.java)
                val lng = snapshot.child("longitude").getValue(Double::class.java)
                if (lat != null && lng != null) {
                    travelerLocation = Pair(lat, lng)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        val locationRef = database.child("tracking").child("current_trip")
        locationRef.addValueEventListener(locationListener)

        // Listen to Chat
        val chatListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messages.clear()
                for (child in snapshot.children) {
                    val text = child.child("text").getValue(String::class.java) ?: ""
                    val isTraveler = child.child("isFromTraveler").getValue(Boolean::class.java) ?: false
                    val time = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val imageBase64 = child.child("imageBase64").getValue(String::class.java)
                    val imageUrl = child.child("imageUrl").getValue(String::class.java)
                    messages.add(ChatMessage(text, isTraveler, time, imageBase64, imageUrl))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        val chatRef = database.child("chat").limitToLast(50)
        chatRef.addValueEventListener(chatListener)

        // Listen to Buzz
        var isInitialLoad = true
        val buzzListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isInitialLoad) {
                    isInitialLoad = false
                    return
                }
                if (snapshot.exists()) {
                    com.varad.unstoppableash.SoundUtil.playShockSound(context)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        val buzzRef = database.child("buzz").child("receiver")
        buzzRef.addValueEventListener(buzzListener)

        // Listen to Alerts
        val alertListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val active = child.child("isSosActive").getValue(Boolean::class.java) ?: false
                    val time = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    if (active && (System.currentTimeMillis() - time) < 15 * 60 * 1000) {
                        isSosActive = true
                    } else {
                        isSosActive = false
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        val alertRef = database.child("alerts").orderByChild("timestamp").limitToLast(1)
        alertRef.addValueEventListener(alertListener)
        
        onDispose {
            vehicleRef.removeEventListener(vehicleListener)
            locationRef.removeEventListener(locationListener)
            chatRef.removeEventListener(chatListener)
            buzzRef.removeEventListener(buzzListener)
            alertRef.removeEventListener(alertListener)
        }
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
        // Map Section (Top Half / Full)
        if (viewMode == "map" || viewMode == "split") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(PremiumSurface)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(16.0)
                        }
                    },
                    update = { mapView ->
                        val geoPoint = GeoPoint(travelerLocation.first, travelerLocation.second)
                        mapView.controller.animateTo(geoPoint)
                        
                        mapView.overlays.clear()
                        val marker = Marker(mapView)
                        marker.position = geoPoint
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        marker.title = "Ash's Live Location"
                        mapView.overlays.add(marker)
                        mapView.invalidate()
                    }
                )
                
                // Map Overlay Header
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = if (isSosActive) Color.Red else PremiumBackground.copy(alpha = 0.8f),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSosActive) {
                                Text("🚨 EMERGENCY SOS TRIGGERED 🚨", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            } else {
                                Box(modifier = Modifier.size(12.dp).background(Color.Green, CircleShape))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Live Tracking Active", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                        if (vehicleInfo.isNotBlank() && !isSosActive) {
                            Text(
                                text = "Vehicle: $vehicleInfo",
                                color = PremiumAccent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                
                if (viewMode == "map") {
                    Button(
                        onClick = { viewMode = "split" },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumAccent)
                    ) {
                        Text("Open Chat", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Chat Section (Bottom Half / Full)
        if (viewMode == "chat" || viewMode == "split") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(PremiumBackground)
            ) {
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
                        ChatBubble(message = message)
                    }
                }

                // Input Area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { FirebaseDatabase.getInstance().reference.child("buzz").child("traveler").setValue(System.currentTimeMillis()) },
                        modifier = Modifier.size(48.dp).background(PremiumSurface, CircleShape)
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.Bolt, contentDescription = "Buzz Traveler", tint = androidx.compose.ui.graphics.Color.Yellow)
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
                                    "isFromTraveler" to false, // Receiver is sending
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
                        Icon(imageVector = Icons.Rounded.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, isTravelerContext: Boolean = false) {
    // If traveler context, 'me' is traveler. If receiver context, 'me' is receiver.
    val isMe = if (isTravelerContext) message.isFromTraveler else !message.isFromTraveler
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    
    // Decode bitmap outside try-catch of composable for backward compatibility with base64
    var decodedBitmap: android.graphics.Bitmap? = null
    if (!message.imageBase64.isNullOrBlank() && message.imageUrl == null) {
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
                    if (message.imageUrl != null) {
                        AsyncImage(
                            model = message.imageUrl,
                            contentDescription = "Selfie",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .padding(bottom = if (message.text.isNotBlank()) 8.dp else 0.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    } else if (decodedBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = decodedBitmap.asImageBitmap(),
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
}
