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
import androidx.compose.material.icons.rounded.Info

import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import kotlinx.coroutines.delay

import androidx.compose.material.icons.rounded.PlayArrow

import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.foundation.clickable



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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

data class FakeCallVoice(val id: String, val name: String, val uri: String?)
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
                    var showUpdateDialog by remember { mutableStateOf(false) }
                    var apkUrl by remember { mutableStateOf("") }
                    val context = androidx.compose.ui.platform.LocalContext.current

                    LaunchedEffect(Unit) {
                        FirebaseAuth.getInstance().signInAnonymously()
                    }

                    DisposableEffect(Unit) {
                        val database = FirebaseDatabase.getInstance().reference
                        val listener = object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val latestVersion = snapshot.child("latest_version_code").getValue(Int::class.java) ?: 1
                                val url = snapshot.child("apk_url").getValue(String::class.java) ?: ""
                                
                                try {
                                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                    val currentVersion = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pInfo).toInt()
                                    
                                    if (latestVersion > currentVersion && url.isNotBlank()) {
                                        apkUrl = url
                                        showUpdateDialog = true
                                    }
                                } catch (e: Exception) {}
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        }
                        val ref = database.child("app_config")
                        ref.addValueEventListener(listener)
                        onDispose { ref.removeEventListener(listener) }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        
                        val sharedPrefs = context.getSharedPreferences("AshGoPrefs", android.content.Context.MODE_PRIVATE)
                        val savedRole = sharedPrefs.getString("user_role", null)
                        val startDest = when (savedRole) {
                            "traveler" -> "traveler_dashboard"
                            "receiver" -> "receiver_home"
                            else -> "role_selection"
                        }
                        
                        LaunchedEffect(savedRole) {
                            if (savedRole == "receiver") {
                                val serviceIntent = Intent(context, ReceiverService::class.java)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(serviceIntent)
                                } else {
                                    context.startService(serviceIntent)
                                }
                            }
                        }
                        
                        NavHost(navController = navController, startDestination = startDest) {
                            composable("role_selection") {
                                RoleSelectionScreen(
                                    onTravelerSelected = { 
                                        sharedPrefs.edit().putString("user_role", "traveler").apply()
                                        navController.navigate("traveler_dashboard") {
                                            popUpTo("role_selection") { inclusive = true }
                                        }
                                    },
                                    onReceiverSelected = { 
                                        sharedPrefs.edit().putString("user_role", "receiver").apply()
                                        val serviceIntent = Intent(context, ReceiverService::class.java)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            context.startForegroundService(serviceIntent)
                                        } else {
                                            context.startService(serviceIntent)
                                        }
                                        navController.navigate("receiver_home") {
                                            popUpTo("role_selection") { inclusive = true }
                                        }
                                    }
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

                        if (showUpdateDialog) {
                            AlertDialog(
                                onDismissRequest = { },
                                title = { Text("Update Available 🚀", color = Color.White) },
                                text = { Text("A new version of Ash Go is ready with the latest features!\n\nTap \"Update Now\" — it will download in the background and ask you to install with one tap.", color = Color.White) },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showUpdateDialog = false
                                            AutoUpdater.downloadAndInstallApk(context, apkUrl)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PremiumAccent)
                                    ) {
                                        Text("Update Now ✨")
                                    }
                                },
                                dismissButton = {
                                    androidx.compose.material3.TextButton(onClick = { showUpdateDialog = false }) {
                                        Text("Later", color = TextSecondary)
                                    }
                                },
                                containerColor = PremiumSurface
                            )
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
            , Manifest.permission.SEND_SMS
            , Manifest.permission.READ_CONTACTS


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

@Composable

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun DashboardScreen(onNavigateToChat: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPrefs = context.getSharedPreferences("AshGoPrefs", android.content.Context.MODE_PRIVATE)
    var busNumber by remember { mutableStateOf(sharedPrefs.getString("vehicle_registration", "") ?: "") }
    var emergencyContacts by remember { mutableStateOf(sharedPrefs.getString("emergency_contacts", "") ?: "") }
    val savedVoicesJson = sharedPrefs.getString("custom_fake_calls", "[]")
    val defaultVoice = FakeCallVoice("default", "Varad (Default)", null)
    var fakeCalls by remember { 
        val list = mutableListOf(defaultVoice)
        try {
            val array = org.json.JSONArray(savedVoicesJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(FakeCallVoice(obj.getString("id"), obj.getString("name"), obj.getString("uri")))
            }
        } catch (e: Exception) {}
        mutableStateOf(list)
    }
    
    var activeMediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }

    LaunchedEffect(activeMediaPlayer, isPlaying) {
        if (activeMediaPlayer != null) {
            duration = activeMediaPlayer!!.duration
            while(isPlaying && activeMediaPlayer != null) {
                try {
                    currentPosition = activeMediaPlayer!!.currentPosition
                } catch (e: Exception) {}
                delay(500)
            }
        }
    }

    var showEmergencyContactsMenu by remember { mutableStateOf(false) }
    var emergencyContactsList by remember { mutableStateOf(sharedPrefs.getString("emergency_contacts", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()) }
    var showFakeCallMenu by remember { mutableStateOf(false) }
    var newVoiceNameDialog by remember { mutableStateOf<android.net.Uri?>(null) }
    var newVoiceNameText by remember { mutableStateOf("") }
    
    val audioPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
            newVoiceNameDialog = uri
            newVoiceNameText = "Custom Voice"
        }
    }

    val contactPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickContact()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val hasPhoneIndex = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER)
                val hasPhone = cursor.getString(hasPhoneIndex).toInt()
                if (hasPhone > 0) {
                    val idIndex = cursor.getColumnIndex(android.provider.ContactsContract.Contacts._ID)
                    val id = cursor.getString(idIndex)
                    val phones = context.contentResolver.query(
                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + id,
                        null,
                        null
                    )
                    if (phones != null && phones.moveToFirst()) {
                        val numIndex = phones.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val number = phones.getString(numIndex)
                        if (!emergencyContactsList.contains(number)) {
                            val updatedList = emergencyContactsList + number
                            emergencyContactsList = updatedList
                            sharedPrefs.edit().putString("emergency_contacts", updatedList.joinToString(",")).apply()
                        }
                        phones.close()
                    }
                }
                cursor.close()
            }
        }
    }

    var isTracking by remember { mutableStateOf(sharedPrefs.getBoolean("is_tracking", false)) }
    var isSosActive by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        val database = com.google.firebase.database.FirebaseDatabase.getInstance().reference
        val alertListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                for (child in snapshot.children) {
                    val active = child.child("isSosActive").getValue(Boolean::class.java) ?: false
                    isSosActive = active
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        database.child("alerts").orderByChild("timestamp").limitToLast(1).addValueEventListener(alertListener)
    }


    DisposableEffect(Unit) {
        val database = FirebaseDatabase.getInstance().reference
        var isInitialLoad = true
        val listener = object : ValueEventListener {
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
        val ref = database.child("buzz").child("traveler")
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
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


    if (newVoiceNameDialog != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { newVoiceNameDialog = null },
            title = { Text("Name this voice") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = newVoiceNameText,
                    onValueChange = { newVoiceNameText = it },
                    label = { Text("E.g. Dad, Brother") }
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val uri = newVoiceNameDialog!!
                    val newVoice = FakeCallVoice(java.util.UUID.randomUUID().toString(), newVoiceNameText, uri.toString())
                    val updatedList = fakeCalls + newVoice
                    fakeCalls = updatedList.toMutableList()
                    
                    val array = org.json.JSONArray()
                    updatedList.filter { it.id != "default" }.forEach {
                        val obj = org.json.JSONObject()
                        obj.put("id", it.id)
                        obj.put("name", it.name)
                        obj.put("uri", it.uri)
                        array.put(obj)
                    }
                    sharedPrefs.edit().putString("custom_fake_calls", array.toString()).apply()
                    newVoiceNameDialog = null
                }) { Text("Save", color = PremiumAccent) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { newVoiceNameDialog = null }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }


    if (showEmergencyContactsMenu) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showEmergencyContactsMenu = false },
            containerColor = PremiumBackground
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Emergency Contacts", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("These contacts will receive SOS SMS alerts.", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                
                if (emergencyContactsList.isEmpty()) {
                    Text("No contacts added yet.", color = TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    emergencyContactsList.forEach { contact ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Rounded.Phone, contentDescription = "Phone", tint = PremiumAccent)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(contact, color = Color.White, fontSize = 16.sp)
                            }
                            androidx.compose.material3.IconButton(onClick = {
                                val updatedList = emergencyContactsList - contact
                                emergencyContactsList = updatedList
                                sharedPrefs.edit().putString("emergency_contacts", updatedList.joinToString(",")).apply()
                            }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = Color.Red.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
                
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { contactPickerLauncher.launch(null) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = "Add", tint = PremiumAccent)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Add Contact", color = PremiumAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showFakeCallMenu) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showFakeCallMenu = false },
            containerColor = PremiumBackground
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Fake Call", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                
                fakeCalls.forEach { voice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showFakeCallMenu = false
                                try {
                                    activeMediaPlayer?.release()
                                    val mediaPlayer = android.media.MediaPlayer()
                                    activeMediaPlayer = mediaPlayer
                                    if (voice.uri == null) {
                                        // Default
                                        val afd = context.resources.openRawResourceFd(R.raw.varad_fake_call)
                                        mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                        afd.close()
                                    } else {
                                        mediaPlayer.setDataSource(context, android.net.Uri.parse(voice.uri))
                                    }
                                    mediaPlayer.prepare()
                                    mediaPlayer.start()
                                    isPlaying = true
                                    mediaPlayer.setOnCompletionListener { 
                                        it.release() 
                                        if (activeMediaPlayer == it) activeMediaPlayer = null
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Error playing audio", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = "Play", tint = PremiumAccent)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(voice.name, color = Color.White, fontSize = 16.sp)
                    }
                }
                
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showFakeCallMenu = false
                            audioPickerLauncher.launch(arrayOf("audio/*"))
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = "Add", tint = PremiumAccent)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Add New Voice", color = PremiumAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
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

        // Settings Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PremiumSurface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
        ) {
            Column {
                                // Vehicle Registration
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DirectionsCar,
                        contentDescription = "Vehicle",
                        tint = PremiumAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Vehicle Registration", fontSize = 12.sp, color = TextSecondary)
                        androidx.compose.foundation.text.BasicTextField(
                            value = busNumber,
                            onValueChange = { 
                                busNumber = it 
                                sharedPrefs.edit().putString("vehicle_registration", it).apply()
                                val database = FirebaseDatabase.getInstance().reference
                                database.child("tracking").child("vehicle_info").setValue(it)
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(PremiumAccent)
                        )
                    }
                }

                androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 20.dp))

                // Location Tracking Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = "Location",
                            tint = PremiumAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Share your location, bbg <33 ", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text(if (isTracking) "Transmitting..." else "Offline", fontSize = 12.sp, color = if (isTracking) PremiumAccent else TextSecondary, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Switch(
                        checked = isTracking,
                        onCheckedChange = { checked ->
                            isTracking = checked
                            sharedPrefs.edit().putBoolean("is_tracking", checked).apply()
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

                androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 20.dp))

                // Fake Call Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showFakeCallMenu = true }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Phone,
                            contentDescription = "Fake Call",
                            tint = PremiumAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Simulate Fake Call", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("Play pre-recorded voice", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = PremiumAccent
                    )
                }

                androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 20.dp))

                // Emergency Contact Picker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { contactPickerLauncher.launch(null) }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Phone,
                            contentDescription = "Contacts",
                            tint = PremiumAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Emergency Contact", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = if (emergencyContacts.isNotBlank()) emergencyContacts else "Tap to select",
                                color = if (emergencyContacts.isNotBlank()) Color.White else TextSecondary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowRight,
                        contentDescription = "Select",
                        tint = TextSecondary
                    )
                }
            }
        }


        if (activeMediaPlayer != null) {
            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = PremiumSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Simulated Call Playing...", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        androidx.compose.material3.IconButton(onClick = { 
                            activeMediaPlayer?.stop()
                            activeMediaPlayer?.release()
                            activeMediaPlayer = null 
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { 
                            currentPosition = it.toInt()
                            try {
                                activeMediaPlayer?.seekTo(currentPosition)
                            } catch (e: Exception) {}
                        },
                        valueRange = 0f..maxOf(1f, duration.toFloat()),
                        colors = SliderDefaults.colors(thumbColor = PremiumAccent, activeTrackColor = PremiumAccent)
                    )

                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.IconButton(onClick = { 
                            activeMediaPlayer?.let {
                                try {
                                    val newPos = maxOf(0, it.currentPosition - 10000)
                                    it.seekTo(newPos)
                                    currentPosition = newPos
                                } catch (e: Exception) {}
                            }
                        }) {
                            Icon(Icons.Rounded.Replay10, contentDescription = "-10s", tint = Color.White)
                        }

                        androidx.compose.material3.IconButton(onClick = { 
                            try {
                                if (isPlaying) {
                                    activeMediaPlayer?.pause()
                                } else {
                                    activeMediaPlayer?.start()
                                }
                                isPlaying = !isPlaying
                            } catch (e: Exception) {}
                        }) {
                            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play/Pause", tint = PremiumAccent, modifier = Modifier.size(32.dp))
                        }

                        androidx.compose.material3.IconButton(onClick = { 
                            activeMediaPlayer?.let {
                                try {
                                    val newPos = minOf(duration, it.currentPosition + 10000)
                                    it.seekTo(newPos)
                                    currentPosition = newPos
                                } catch (e: Exception) {}
                            }
                        }) {
                            Icon(Icons.Rounded.Forward10, contentDescription = "+10s", tint = Color.White)
                        }
                    }
                }
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
            Text("Chat with Varad", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Minimalist Premium SOS Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(180.dp)
                .padding(bottom = 16.dp)
                .scale(scale)
                .background(if (isSosActive) Color.Red.copy(alpha = 0.8f) else PremiumSOS, CircleShape)
                .border(4.dp, if (isSosActive) Color.Red else SOSGlow, CircleShape)
                .combinedClickable(
                    onLongClick = {
                        val database = FirebaseDatabase.getInstance().reference
                        if (!isSosActive) {
                            val alertData = mapOf(
                                "isSosActive" to true,
                                "timestamp" to System.currentTimeMillis()
                            )
                            database.child("alerts").push().setValue(alertData)
                            Toast.makeText(context, "SOS TRIGGERED", Toast.LENGTH_SHORT).show()
                        } else {
                            val alertData = mapOf(
                                "isSosActive" to false,
                                "timestamp" to System.currentTimeMillis()
                            )
                            database.child("alerts").push().setValue(alertData)
                            Toast.makeText(context, "SOS STOPPED", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onClick = {
                        if (isSosActive) {
                            Toast.makeText(context, "Long press to STOP SOS", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Long press to trigger SOS", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isSosActive) "STOP" else "SOS",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.W900,
                    color = Color.White,
                    letterSpacing = 4.sp
                )
                Text(
                    text = if (isSosActive) "HOLD TO CANCEL" else "HOLD TO ALERT",
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    
    var lat by remember { mutableStateOf<Double?>(null) }
    var lng by remember { mutableStateOf<Double?>(null) }
    var locationAddress by remember { mutableStateOf("Fetching location...") }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    LaunchedEffect(lat, lng) {
        if (lat != null && lng != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                    val addresses = geocoder.getFromLocation(lat!!, lng!!, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val loc = buildString {
                            if (address.subLocality != null) append(address.subLocality).append(", ")
                            if (address.locality != null) append(address.locality)
                        }.trimEnd(',', ' ')
                        
                        locationAddress = loc.ifBlank { "Loc: ${String.format("%.4f", lat!!)}, ${String.format("%.4f", lng!!)}" }
                    } else {
                        locationAddress = "Loc: ${String.format("%.4f", lat!!)}, ${String.format("%.4f", lng!!)}"
                    }
                } catch (e: Exception) {
                    locationAddress = "Loc: ${String.format("%.4f", lat!!)}, ${String.format("%.4f", lng!!)}"
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val database = FirebaseDatabase.getInstance().reference
        
        val vehicleListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                vehicleInfo = snapshot.getValue(String::class.java) ?: "Not provided"
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        val vehicleRef = database.child("tracking").child("vehicle_info")
        vehicleRef.addValueEventListener(vehicleListener)

        val tripListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val time = snapshot.child("timestamp").getValue(Long::class.java)
                if (time != null) lastUpdated = time
                
                val newLat = snapshot.child("latitude").getValue(Double::class.java)
                val newLng = snapshot.child("longitude").getValue(Double::class.java)
                if (newLat != null && newLng != null) {
                    lat = newLat
                    lng = newLng
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        val tripRef = database.child("tracking").child("current_trip")
        tripRef.addValueEventListener(tripListener)

        val alertListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val active = child.child("isSosActive").getValue(Boolean::class.java) ?: false
                    val time = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    isSosActive = active && (System.currentTimeMillis() - time) < 15 * 60 * 1000
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        val alertRef = database.child("alerts").orderByChild("timestamp").limitToLast(1)
        alertRef.addValueEventListener(alertListener)
        
        onDispose {
            vehicleRef.removeEventListener(vehicleListener)
            tripRef.removeEventListener(tripListener)
            alertRef.removeEventListener(alertListener)
        }
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
            text = "Varad's Dashboard",
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
                        locationAddress
                    } else "No location data yet"
                    Text(timeText, color = if (lastUpdated > 0 && (System.currentTimeMillis() - lastUpdated) > 300000) Color.Red else Color.White, fontSize = 16.sp)
                }
                if (lastUpdated > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, contentDescription = "Time", tint = TextSecondary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        val dateFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                        val formattedTime = dateFormat.format(java.util.Date(lastUpdated))
                        Text("last updated on: $formattedTime", color = TextSecondary, fontSize = 14.sp)
                    }
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
