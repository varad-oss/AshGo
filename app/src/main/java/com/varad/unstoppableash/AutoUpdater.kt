package com.varad.unstoppableash

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat

object AutoUpdater {
    /**
     * Resolves Google Drive share/uc URLs into direct download URLs
     * that bypass the virus-scan warning page for large files.
     */
    private fun resolveDownloadUrl(url: String): String {
        // Pattern: https://drive.google.com/uc?export=download&id=FILE_ID
        // or:      https://drive.google.com/file/d/FILE_ID/view?...
        val filePattern = Regex("drive\\.google\\.com/file/d/([^/]+)")
        val ucPattern = Regex("drive\\.google\\.com/uc\\?.*id=([^&]+)")

        val fileId = filePattern.find(url)?.groupValues?.get(1)
            ?: ucPattern.find(url)?.groupValues?.get(1)

        return if (fileId != null) {
            // Use the confirmed direct download URL that skips the virus-scan page
            "https://drive.google.com/uc?export=download&confirm=t&id=$fileId"
        } else {
            url // Not a Drive URL, use as-is
        }
    }

    fun downloadAndInstallApk(context: Context, url: String) {
        val resolvedUrl = resolveDownloadUrl(url)
        Toast.makeText(context, "Downloading update...", Toast.LENGTH_LONG).show()

        // Delete any old update APK first to avoid stale installs
        val destFile = java.io.File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "AshGo_Update.apk"
        )
        if (destFile.exists()) destFile.delete()

        val request = DownloadManager.Request(Uri.parse(resolvedUrl))
            .setTitle("AshGo Update")
            .setDescription("Downloading the latest features...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AshGo_Update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setMimeType("application/vnd.android.package-archive")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId == id) {
                    // Verify download succeeded
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        if (statusIdx != -1 && cursor.getInt(statusIdx) == DownloadManager.STATUS_SUCCESSFUL) {
                            val uri = downloadManager.getUriForDownloadedFile(downloadId)
                            val installIntent = Intent(Intent.ACTION_VIEW)
                            installIntent.setDataAndType(uri, "application/vnd.android.package-archive")
                            installIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                            try {
                                context.startActivity(installIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to launch installer", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Download failed. Check your connection.", Toast.LENGTH_LONG).show()
                        }
                        cursor.close()
                    }
                    try { context.unregisterReceiver(this) } catch (e: Exception) {}
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }
}
