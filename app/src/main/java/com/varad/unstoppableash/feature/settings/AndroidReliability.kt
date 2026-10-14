package com.varad.unstoppableash.feature.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.varad.unstoppableash.domain.notify.Channel
import com.varad.unstoppableash.service.connect.StayConnectedService

/** Reads the real Android state behind each Reliability check. */
fun readReliability(context: Context): ReliabilityChecks {
    val nm = context.getSystemService(NotificationManager::class.java)
    val sos = nm?.getNotificationChannel(Channel.SOS.id)
    return ReliabilityChecks(
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        sosChannelOn = sos == null || sos.importance >= NotificationManager.IMPORTANCE_HIGH, // not yet created = default (high)
        fullScreenAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || nm?.canUseFullScreenIntent() == true,
        batteryUnrestricted = context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true,
        locationPrecise = granted(context, Manifest.permission.ACCESS_FINE_LOCATION),
        smsAllowed = granted(context, Manifest.permission.SEND_SMS),
        connected = false, // filled in from the live connection by the view-model
    )
}

/** Opens the exact Android screen that fixes a check. Nothing is changed without the person doing it there. */
fun openFix(context: Context, fix: Fix) {
    val pkg = context.packageName
    val intent = when (fix) {
        Fix.NOTIFICATION_SETTINGS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        Fix.SOS_CHANNEL_SETTINGS -> Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg).putExtra(Settings.EXTRA_CHANNEL_ID, Channel.SOS.id)
        Fix.FULL_SCREEN_SETTINGS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", pkg, null))
        } else appDetails(pkg)
        Fix.BATTERY_SETTINGS -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        Fix.LOCATION_PERMISSION, Fix.SMS_PERMISSION -> appDetails(pkg)
        Fix.RESTART_CONNECTION -> { StayConnectedService.start(context); return }
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { context.startActivity(appDetails(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

private fun appDetails(pkg: String) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
