package com.varad.unstoppableash.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class UpdatePolicyTest {
    private val sha = "a".repeat(64)
    private val good = mapOf(
        "latest_version_code" to 12L, "latest_version_name" to "2.0.1",
        "apk_url" to "https://github.com/varad-oss/AshGo-releases/releases/download/v2.0.1/ashgo-2.0.1.apk",
        "sha256" to sha, "apk_size" to 8_123_456L, "notes" to "Fixes",
    )

    @Test fun aNewerSignedOffReleaseIsOffered() {
        val info = UpdatePolicy.parse(good, installedVersionCode = 11) as UpdateCheck.Available
        assertEquals(12, info.info.versionCode); assertEquals(sha, info.info.sha256); assertEquals(8_123_456L, info.info.sizeBytes)
    }

    @Test fun sameOrOlderIsUpToDate() {
        assertEquals(UpdateCheck.UpToDate, UpdatePolicy.parse(good, installedVersionCode = 12))
        assertEquals("never a downgrade", UpdateCheck.UpToDate, UpdatePolicy.parse(good, installedVersionCode = 20))
    }

    @Test fun missingOrMalformedConfigCannotBeChecked() {
        assertEquals(UpdateCheck.Unavailable, UpdatePolicy.parse(null, 1))
        assertEquals("no checksum, no install", UpdateCheck.Unavailable, UpdatePolicy.parse(good - "sha256", 1))
        assertEquals(UpdateCheck.Unavailable, UpdatePolicy.parse(good + ("sha256" to "xyz"), 1))
    }

    @Test fun onlyHttpsFromTheReleasesRepo() {
        assertTrue(UpdatePolicy.allowedUrl(good["apk_url"] as String))
        assertTrue("GitHub's asset CDN after redirect", UpdatePolicy.allowedUrl("https://objects.githubusercontent.com/github-production-release-asset/1/2"))
        assertFalse(UpdatePolicy.allowedUrl("http://github.com/varad-oss/AshGo-releases/releases/download/v1/a.apk"))
        assertFalse(UpdatePolicy.allowedUrl("https://github.com/someone-else/AshGo-releases/releases/download/v1/a.apk"))
        assertFalse(UpdatePolicy.allowedUrl("https://github.com.evil.example/varad-oss/AshGo-releases/a.apk"))
        assertFalse(UpdatePolicy.allowedUrl("https://evil.example/?u=https://github.com/varad-oss/AshGo-releases/"))
        assertEquals(UpdateCheck.Unavailable, UpdatePolicy.parse(good + ("apk_url" to "https://evil.example/a.apk"), 1))
    }

    @Test fun checksumCompareIsExactAndCaseInsensitive() {
        val bytes = "apk-bytes".toByteArray()
        val hex = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertTrue(UpdatePolicy.matches(bytes.inputStream(), hex.uppercase()))
        assertFalse(UpdatePolicy.matches(bytes.inputStream(), hex.replaceFirst(hex[0], if (hex[0] == 'a') 'b' else 'a')))
    }

    @Test fun dotSegmentsCannotWalkOutOfTheReleasesRepo() {
        assertFalse(UpdatePolicy.allowedUrl("https://github.com/varad-oss/AshGo-releases/../other-repo/releases/download/v1/a.apk"))
        assertFalse("percent-encoded dots too", UpdatePolicy.allowedUrl("https://github.com/varad-oss/AshGo-releases/%2e%2e/other-repo/a.apk"))
        assertFalse(UpdatePolicy.allowedUrl("https://github.com/varad-oss/AshGo-releases/./releases/download/v1/a.apk"))
    }

    @Test fun downloadsStopAtTheAnnouncedSizeOrTheHardCap() {
        assertFalse(UpdatePolicy.tooLarge(downloaded = 8_123_456L, announced = 8_123_456L))
        assertTrue("one byte more than announced", UpdatePolicy.tooLarge(downloaded = 8_123_457L, announced = 8_123_456L))
        assertFalse(UpdatePolicy.tooLarge(downloaded = UpdatePolicy.MAX_APK_BYTES, announced = null))
        assertTrue("no size announced: hard cap", UpdatePolicy.tooLarge(downloaded = UpdatePolicy.MAX_APK_BYTES + 1, announced = null))
        assertTrue("an announced size above the cap is not trusted", UpdatePolicy.tooLarge(downloaded = UpdatePolicy.MAX_APK_BYTES + 1, announced = Long.MAX_VALUE))
    }

    @Test fun parseNeverThrows() {
        assertEquals(UpdateCheck.Unavailable, UpdatePolicy.parse(mapOf("latest_version_code" to "twelve"), 1))
        assertNull((UpdatePolicy.parse(good - "notes", 1) as UpdateCheck.Available).info.notes)
    }
}
