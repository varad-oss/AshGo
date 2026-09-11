import re

with open("app/src/main/java/com/varad/unstoppableash/MainActivity.kt", "r") as f:
    content = f.read()

# 1. Update setContent block
set_content_pattern = re.compile(
    r'(setContent \{\s*AshGoTheme\(darkTheme = true\) \{\s*Surface\(\s*modifier = Modifier\.fillMaxSize\(\),\s*color = MaterialTheme\.colorScheme\.background\s*\) \{)(.*?)(\s*\n\s*\}\s*\}\s*\})',
    re.DOTALL
)

update_logic = r"""\1
                    var showUpdateDialog by remember { mutableStateOf(false) }
                    var apkUrl by remember { mutableStateOf("") }
                    val context = androidx.compose.ui.platform.LocalContext.current

                    LaunchedEffect(Unit) {
                        val database = com.google.firebase.database.FirebaseDatabase.getInstance().reference
                        database.child("app_config").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
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
                            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                        })
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
\2
                        if (showUpdateDialog) {
                            AlertDialog(
                                onDismissRequest = { /* Force update */ },
                                title = { Text("Update Available! 🚀", color = Color.White) },
                                text = { Text("A new surprise feature has been added! Tap below to update instantly.", color = Color.White) },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showUpdateDialog = false
                                            AutoUpdater.downloadAndInstallApk(context, apkUrl)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PremiumAccent)
                                    ) {
                                        Text("Update Now")
                                    }
                                },
                                containerColor = PremiumSurface
                            )
                        }
                    }\3"""

content = set_content_pattern.sub(update_logic, content)

with open("app/src/main/java/com/varad/unstoppableash/MainActivity.kt", "w") as f:
    f.write(content)
