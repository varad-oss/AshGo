package com.varad.unstoppableash.feature.ourpage

import java.io.File

/**
 * "Our page" photos (O-18): picked on this phone, shrunk ([encode]) into app storage, never uploaded or bundled.
 * File names are creation times, so the list keeps the order they were added.
 */
class OurPhotosStore(private val dir: File, private val encode: suspend (uri: String) -> ByteArray?, private val now: () -> Long) {

    fun list(): List<File> = dir.listFiles { f -> f.isFile && f.name.endsWith(EXT) }.orEmpty().sortedBy { it.name }

    /** How many were added; unreadable photos are skipped and nothing is added past [MAX]. */
    suspend fun add(uris: List<String>): Int {
        dir.mkdirs()
        var added = 0
        for (uri in uris) {
            if (list().size >= MAX) break
            val bytes = encode(uri) ?: continue
            File(dir, "%015d$EXT".format(now())).writeBytes(bytes)
            added++
        }
        return added
    }

    fun remove(name: String) {
        val f = File(dir, name)
        if (f.parentFile?.canonicalPath == dir.canonicalPath && f.name.endsWith(EXT)) f.delete()
    }

    companion object {
        const val MAX = 24
        private const val EXT = ".jpg"
    }
}
