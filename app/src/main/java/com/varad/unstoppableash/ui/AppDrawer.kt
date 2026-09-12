package com.varad.unstoppableash.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

val PremiumBackground = Color(0xFF0F0F13)
val PremiumSurface = Color(0xFF1C1C22)
val PremiumAccent = Color(0xFF635BFF)
val TextSecondary = Color(0xFF8E8E93)


@Composable
fun AppDrawer(role: String, onNavigate: (String) -> Unit) {
    var profilePicBase64 by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(role) {
        if (role.isBlank()) return@LaunchedEffect
        val ref = FirebaseDatabase.getInstance().reference.child("users").child(role).child("profilePicBase64")
        ref.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                profilePicBase64 = snapshot.getValue(String::class.java)
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val out = ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 40, out)
                    val base64 = Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
                    FirebaseDatabase.getInstance().reference.child("users").child(role).child("profilePicBase64").setValue(base64)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    var profileBmp: android.graphics.Bitmap? = null
    if (profilePicBase64 != null) {
        try {
            val bytes = Base64.decode(profilePicBase64, Base64.DEFAULT)
            profileBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {}
    }

    ModalDrawerSheet(
        drawerContainerColor = PremiumBackground,
        modifier = Modifier.width(280.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header / Profile Picture
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(PremiumSurface)
                    .clickable { launcher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (profileBmp != null) {
                    Image(
                        bitmap = profileBmp.asImageBitmap(),
                        contentDescription = "Profile Pic",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = PremiumAccent, modifier = Modifier.size(48.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (role == "traveler") "Traveler (Diva 💅)" else "Receiver (Varad 👑)",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tap photo to change",
                color = PremiumAccent,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp).clickable { launcher.launch("image/*") }
            )

            Spacer(modifier = Modifier.height(48.dp))

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(24.dp))

            DrawerItem(icon = Icons.Rounded.Help, label = "Help") {
                android.widget.Toast.makeText(context, "Help coming soon!", android.widget.Toast.LENGTH_SHORT).show()
            }
            Spacer(modifier = Modifier.height(16.dp))
            DrawerItem(icon = Icons.Rounded.Info, label = "About Us") {
                android.widget.Toast.makeText(context, "Made with ❤️ by Varad", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun DrawerItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = TextSecondary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
