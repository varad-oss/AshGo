import re

with open("app/src/main/java/com/varad/unstoppableash/MainActivity.kt", "r") as f:
    content = f.read()

# 1. State variables
state_vars = """    var vehicleInfo by remember { mutableStateOf("Waiting for data...") }
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
                        
                        locationAddress = loc.ifBlank { "Loc: ${String.format("%.4f", lat)}, ${String.format("%.4f", lng)}" }
                    } else {
                        locationAddress = "Loc: ${String.format("%.4f", lat)}, ${String.format("%.4f", lng)}"
                    }
                } catch (e: Exception) {
                    locationAddress = "Loc: ${String.format("%.4f", lat)}, ${String.format("%.4f", lng)}"
                }
            }
        }
    }"""

content = re.sub(
    r'    var vehicleInfo.*?var isSosActive by remember \{ mutableStateOf\(false\) \}',
    state_vars,
    content,
    flags=re.DOTALL
)

# 2. Update the firebase listener
listener = """        database.child("tracking").child("current_trip").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val time = snapshot.child("timestamp").getValue(Long::class.java)
                if (time != null) lastUpdated = time
                
                val newLat = snapshot.child("latitude").getValue(Double::class.java)
                val newLng = snapshot.child("longitude").getValue(Double::class.java)
                if (newLat != null && newLng != null) {
                    lat = newLat
                    lng = newLng
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })"""

content = re.sub(
    r'        database\.child\("tracking"\)\.child\("current_trip"\).*?override fun onCancelled\(error: com\.google\.firebase\.database\.DatabaseError\) \{\}\n        \}\)',
    listener,
    content,
    flags=re.DOTALL
)

# 3. Update the UI text
ui_text_pattern = re.compile(
    r'(val timeText = if \(lastUpdated > 0\) \{[\s\S]*?\} else "No location data yet"\s*Text\(timeText, color = )if \(lastUpdated > 0 && \(System\.currentTimeMillis\(\) - lastUpdated\) > 300000\) Color\.Red else Color\.White(, fontSize = 16\.sp\))',
    re.DOTALL
)

def ui_text_replacer(match):
    return """val timeText = if (lastUpdated > 0) {
                        locationAddress
                    } else "No location data yet"
                    Text(timeText, color = if (lastUpdated > 0 && (System.currentTimeMillis() - lastUpdated) > 300000) Color.Red else Color.White, fontSize = 16.sp)"""

content = ui_text_pattern.sub(ui_text_replacer, content)


with open("app/src/main/java/com/varad/unstoppableash/MainActivity.kt", "w") as f:
    f.write(content)
