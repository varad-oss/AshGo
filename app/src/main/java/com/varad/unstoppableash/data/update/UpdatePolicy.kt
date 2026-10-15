package com.varad.unstoppableash.data.update

import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

data class UpdateInfo(val versionCode: Int, val versionName: String?, val url: String, val sha256: String, val sizeBytes: Long?, val notes: String?)

sealed interface UpdateCheck {
    data class Available(val info: UpdateInfo) : UpdateCheck
    data object UpToDate : UpdateCheck
    data object Unavailable : UpdateCheck
}

/**
 * What the app accepts as an update (D-10). Nothing here needs a token: the owner publishes version, URL and
 * SHA-256 in the read-only `app_config`. An update must be newer, come over https from the releases repo (or
 * GitHub's asset CDN it redirects to), and match its checksum byte for byte, or it is never installed.
 */
object UpdatePolicy {
    private const val REPO_PATH = "/varad-oss/AshGo-releases/"
    private val ASSET_HOSTS = setOf("objects.githubusercontent.com", "release-assets.githubusercontent.com")
    private val HEX64 = Regex("^[0-9a-fA-F]{64}$")

    fun parse(config: Map<*, *>?, installedVersionCode: Int): UpdateCheck {
        if (config == null) return UpdateCheck.Unavailable
        val code = (config["latest_version_code"] as? Number)?.toInt() ?: return UpdateCheck.Unavailable
        val url = (config["apk_url"] as? String)?.trim()?.takeIf(::allowedUrl) ?: return UpdateCheck.Unavailable
        val sha = (config["sha256"] as? String)?.trim()?.takeIf { HEX64.matches(it) } ?: return UpdateCheck.Unavailable
        if (code <= installedVersionCode) return UpdateCheck.UpToDate
        return UpdateCheck.Available(
            UpdateInfo(
                versionCode = code,
                versionName = config["latest_version_name"] as? String,
                url = url,
                sha256 = sha.lowercase(),
                sizeBytes = (config["apk_size"] as? Number)?.toLong()?.takeIf { it > 0 },
                notes = (config["notes"] as? String)?.take(500),
            ),
        )
    }

    /** https only; github.com paths must be the owner's releases repo; or GitHub's release-asset CDN. */
    fun allowedUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.userInfo != null || (uri.port != -1 && uri.port != 443)) return false
        val host = uri.host?.lowercase() ?: return false
        return when (host) {
            "github.com" -> uri.path?.startsWith(REPO_PATH) == true
            in ASSET_HOSTS -> true
            else -> false
        }
    }

    fun matches(input: InputStream, expectedSha256: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return MessageDigest.isEqual(actual.toByteArray(), expectedSha256.lowercase().toByteArray())
    }
}
