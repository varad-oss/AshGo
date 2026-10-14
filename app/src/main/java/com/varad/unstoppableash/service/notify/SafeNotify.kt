package com.varad.unstoppableash.service.notify

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Posts only when Android will show it; a permission revoked a moment ago is not a crash. */
fun safeNotify(context: Context, tag: String, id: Int, notification: Notification): Boolean {
    val nm = NotificationManagerCompat.from(context)
    if (!nm.areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false
    return try {
        nm.notify(tag, id, notification); true
    } catch (e: SecurityException) {
        false
    }
}
