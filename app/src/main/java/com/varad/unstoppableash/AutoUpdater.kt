package com.varad.unstoppableash

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object AutoUpdater {
    /**
     * Resolves Google Drive share/uc URLs into direct download URLs
     * that bypass the virus-scan warning page for large files.
     */
    private fun resolveDownloadUrl(url: String): String {
        val filePattern = Regex("drive\\.google\\.com/file/d/([^/]+)")
        val ucPattern = Regex("drive\\.google\\.com/uc\\?.*id=([^&]+)")

        val fileId = filePattern.find(url)?.groupValues?.get(1)
            ?: ucPattern.find(url)?.groupValues?.get(1)

        return if (fileId != null) {
            "https://drive.google.com/uc?export=download&confirm=t&id=$fileId"
        } else {
            url
        }
    }

    fun downloadAndInstallApk(context: Context, url: String, token: String? = null) {
        val resolvedUrl = resolveDownloadUrl(url)
        Toast.makeText(context, "Downloading update... Please wait.", Toast.LENGTH_LONG).show()

        val destFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "AshGo_Update.apk")
        if (destFile.exists()) destFile.delete()

        Thread {
            try {
                val connection = URL(resolvedUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 60000
                
                if (!token.isNullOrBlank()) {
                    // Send authorization to bypass private repo restrictions
                    connection.setRequestProperty("Authorization", "token $token")
                }

                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val input = connection.inputStream
                    val output = FileOutputStream(destFile)
                    input.copyTo(output)
                    output.close()
                    input.close()

                    // Launch install intent using FileProvider
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        destFile
                    )

                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }

                    Handler(Looper.getMainLooper()).post {
                        try {
                            context.startActivity(installIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to launch installer", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Download failed: HTTP ${connection.responseCode}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Download failed. Check your connection.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
