package com.varad.unstoppableash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.varad.unstoppableash.ui.theme.*

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Toast

import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestPermissions()

        setContent {
            AshGoTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "role_selection") {
                        composable("role_selection") {
                            RoleSelectionScreen(
                                onTravelerSelected = { navController.navigate("traveler_dashboard") },
                                onReceiverSelected = { navController.navigate("receiver_home") }
                            )
                        }
                        composable("traveler_dashboard") {
                            DashboardScreen(
                                onNavigateToChat = { navController.navigate("traveler_chat") }
                            )
                        }
                        composable("traveler_chat") {
                            com.varad.unstoppableash.ui.TravelerChatScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("receiver_home") {
                            ReceiverDashboard(
                                onNavigateToMap = { navController.navigate("receiver_map_only") },
                                onNavigateToChat = { navController.navigate("receiver_chat_only") }
                            )
                        }
                        composable("receiver_map") { // Legacy split route just in case
                            com.varad.unstoppableash.ui.ReceiverScreen(initialMode = "split")
                        }
                        composable("receiver_map_only") {
                            com.varad.unstoppableash.ui.ReceiverScreen(initialMode = "map")
                        }
                        composable("receiver_chat_only") {
                            com.varad.unstoppableash.ui.ReceiverScreen(initialMode = "chat")
                        }
                    }
                }
            }
        }
    }
    
    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 100)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onNavigateToChat: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var busNumber by remember { mutableStateOf("") }
    var isTracking by remember { mutableStateOf(false) }


    LaunchedEffect(Unit) {
        val database = com.google.firebase.database.FirebaseDatabase.getInstance().reference
        database.child("buzz").child("traveler").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (snapshot.exists()) {
                    val timestamp = snapshot.getValue(Long::class.java) ?: 0L
                    if (System.currentTimeMillis() - timestamp < 10000) {
                        com.varad.unstoppableash.SoundUtil.playShockSound(context)
                    }
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    // Subtle breathing animation for the SOS button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sos_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // Premium Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = "Shield",
                tint = PremiumAccent,
                modifier = Modifier.size(48.dp).padding(bottom = 12.dp)
            )
            Text(
                text = "Ash Go",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 1.sp
            )
            Text(
                text = "Active Protection System",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
                letterSpacing = 0.5.sp
            )
        }

        // Modern Input Field
        OutlinedTextField(
            value = busNumber,
            onValueChange = { 
                busNumber = it 
                val database = com.google.firebase.database.FirebaseDatabase.getInstance().reference
                database.child("tracking").child("vehicle_info").setValue(it)
            },
            label = { Text("Vehicle Registration") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.DirectionsCar,
                    contentDescription = "Vehicle",
                    tint = TextSecondary
                )
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PremiumAccent,
                unfocusedBorderColor = PremiumSurface,
                focusedContainerColor = PremiumSurface,
                unfocusedContainerColor = PremiumSurface,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = PremiumAccent
            )
        )

        // Sleek Glassmorphic-style Tracking Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PremiumSurface,
            modifier = Modifier.fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(PremiumAccent.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = "Location",
                            tint = PremiumAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tap to share your location, My Princess", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                        Text(if (isTracking) "Transmitting..." else "Offline", fontSize = 12.sp, color = if (isTracking) PremiumAccent else TextSecondary)
                    }
                }
                Switch(
                    checked = isTracking,
                    onCheckedChange = { checked ->
                        isTracking = checked
                        val serviceIntent = Intent(context, TrackingService::class.java)
                        if (checked) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        } else {
                            serviceIntent.action = "ACTION_STOP"
                            context.startService(serviceIntent)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PremiumAccent,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = PremiumBackground
                    )
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onNavigateToChat,
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, PremiumAccent),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text("Open Guardian Chat", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Minimalist Premium SOS Button
        Button(
            onClick = {
                val database = com.google.firebase.database.FirebaseDatabase.getInstance().reference
                val alertData = mapOf(
                    "isSosActive" to true,
                    "timestamp" to System.currentTimeMillis()
                )
                database.child("alerts").push().setValue(alertData)
                Toast.makeText(context, "SOS TRIGGERED", Toast.LENGTH_SHORT).show()
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = PremiumSOS),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .size(180.dp)
                .padding(bottom = 16.dp)
                .scale(scale)
                // Fake glow effect
                .border(4.dp, SOSGlow, CircleShape)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "SOS",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.W900,
                    color = Color.White,
                    letterSpacing = 4.sp
                )
                Text(
                    text = "HOLD TO ALERT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    AshGoTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            DashboardScreen()
        }
    }
}

@Composable
fun RoleSelectionScreen(onTravelerSelected: () -> Unit, onReceiverSelected: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Ash Go",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Select your role to continue",
            fontSize = 16.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 48.dp)
        )

        Button(
            onClick = onTravelerSelected,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PremiumAccent)
        ) {
            Text("I am the Traveler", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onReceiverSelected,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, PremiumAccent),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text("I am the Receiver", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ReceiverDashboard(onNavigateToMap: () -> Unit, onNavigateToChat: () -> Unit) {
    var vehicleInfo by remember { mutableStateOf("Waiting for data...") }
    var lastUpdated by remember { mutableStateOf(0L) }
    var isSosActive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val database = com.google.firebase.database.FirebaseDatabase.getInstance().reference
        
        database.child("tracking").child("vehicle_info").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                vehicleInfo = snapshot.getValue(String::class.java) ?: "Not provided"
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })

        database.child("tracking").child("current_trip").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val time = snapshot.child("timestamp").getValue(Long::class.java)
                if (time != null) lastUpdated = time
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })

        database.child("alerts").orderByChild("timestamp").limitToLast(1).addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                for (child in snapshot.children) {
                    val active = child.child("isSosActive").getValue(Boolean::class.java) ?: false
                    val time = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    isSosActive = active && (System.currentTimeMillis() - time) < 15 * 60 * 1000
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Shield,
            contentDescription = "Shield",
            tint = if (isSosActive) Color.Red else PremiumAccent,
            modifier = Modifier.size(64.dp).padding(bottom = 16.dp)
        )
        Text(
            text = "Guardian Dashboard",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        // Info Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PremiumSurface,
            modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DirectionsCar, contentDescription = "Vehicle", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Vehicle: $vehicleInfo", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = "Location", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    val timeText = if (lastUpdated > 0) {
                        val mins = (System.currentTimeMillis() - lastUpdated) / 60000
                        if (mins == 0L) "Updated just now" else "Updated $mins min ago"
                    } else "No location data yet"
                    Text(timeText, color = if (lastUpdated > 0 && (System.currentTimeMillis() - lastUpdated) > 300000) Color.Red else Color.White, fontSize = 16.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onNavigateToMap,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PremiumAccent)
        ) {
            Text("View Live Location", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onNavigateToChat,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, PremiumAccent),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text("Chat With Her", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}