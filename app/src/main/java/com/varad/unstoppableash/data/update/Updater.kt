package com.varad.unstoppableash.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.data.firebase.RtdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class UpdateFailure { NETWORK, NOT_ALLOWED, CHECKSUM, STORAGE }

/** What the Updates screen needs; [Updater] is the real one. */
interface UpdateSource {
    suspend fun check(): UpdateCheck
    suspend fun download(info: UpdateInfo, onProgress: (Float?) -> Unit): Outcome<File>
    fun install(file: File)
}

/**
 * Check → download → verify → system installer (D-10). Redirects are followed by hand so every hop is checked
 * against [UpdatePolicy.allowedUrl]; the file is only offered to the installer after its SHA-256 matches.
 */
class Updater(private val context: Context, private val db: RtdbClient, private val installedVersionCode: () -> Int) : UpdateSource {

    override suspend fun check(): UpdateCheck {
        val value = withTimeoutOrNull(15_000) { db.get(APP_CONFIG) } ?: return UpdateCheck.Unavailable
        return UpdatePolicy.parse((value as? Outcome.Success)?.value as? Map<*, *>, installedVersionCode())
    }

    override suspend fun download(info: UpdateInfo, onProgress: (Float?) -> Unit): Outcome<File> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // one download at a time; old ones are junk
        val out = File(dir, "ashgo-${info.versionCode}.apk")
        try {
            var url = info.url
            var connection: HttpURLConnection
            var hops = 0
            while (true) {
                if (!UpdatePolicy.allowedUrl(url)) return@withContext Outcome.Failure(FailureReason.INVALID, UpdateFailure.NOT_ALLOWED.name)
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15_000; readTimeout = 30_000
                    setRequestProperty("User-Agent", "AshGo-updater")
                }
                val code = connection.responseCode
                if (code in 300..399) {
                    url = connection.getHeaderField("Location") ?: return@withContext Outcome.Failure(FailureReason.UNKNOWN, UpdateFailure.NETWORK.name)
                    connection.disconnect()
                    if (++hops > 5) return@withContext Outcome.Failure(FailureReason.UNKNOWN, UpdateFailure.NETWORK.name)
                    continue
                }
                if (code != 200) return@withContext Outcome.Failure(FailureReason.UNKNOWN, UpdateFailure.NETWORK.name)
                break
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            connection.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        done += n
                        if (UpdatePolicy.tooLarge(done, info.sizeBytes)) {
                            connection.disconnect()
                            out.delete()
                            return@withContext Outcome.Failure(FailureReason.INVALID, UpdateFailure.CHECKSUM.name)
                        }
                        output.write(buffer, 0, n)
                        onProgress(total?.let { (done.toFloat() / it).coerceAtMost(1f) })
                    }
                }
            }
            connection.disconnect()
            if (!UpdatePolicy.matches(out.inputStream(), info.sha256)) {
                out.delete()
                return@withContext Outcome.Failure(FailureReason.INVALID, UpdateFailure.CHECKSUM.name)
            }
            Outcome.Success(out)
        } catch (e: java.io.IOException) {
            out.delete()
            Outcome.Failure(FailureReason.OFFLINE, UpdateFailure.NETWORK.name)
        }
    }

    /** Hands a verified file to Android's installer; the person confirms there. Same signing key or Android refuses. */
    override fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        const val APP_CONFIG = "app_config"
    }
}
