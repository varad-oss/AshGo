package com.varad.unstoppableash.feature.fakecall

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.varad.unstoppableash.R
import com.varad.unstoppableash.domain.model.FakeCallVoice
import java.io.File

/** The phone's own ringtone, then the recording on the earpiece like a real call (USAGE_VOICE_COMMUNICATION). */
class AndroidFakeCallAudio(context: Context) : FakeCallAudio {
    private val app = context.applicationContext
    private val audioManager = app.getSystemService(AudioManager::class.java)
    private var ringtone: Ringtone? = null
    private var player: MediaPlayer? = null
    private var previousMode: Int? = null

    override fun ring() {
        stop()
        ringtone = runCatching {
            RingtoneManager.getRingtone(app, RingtoneManager.getActualDefaultRingtoneUri(app, RingtoneManager.TYPE_RINGTONE) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
        }.getOrNull()?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            play()
        }
    }

    override fun play(voice: FakeCallVoice?) {
        stop()
        previousMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                if (voice == null) {
                    app.resources.openRawResourceFd(R.raw.varad_fake_call).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                } else {
                    setDataSource(app, voice.uri.toUri())
                }
                prepare()
                start()
            }
        }.onFailure { Log.w(TAG, "voice not playable: ${it.javaClass.simpleName}") }.getOrNull()
    }

    override fun stop() {
        ringtone?.stop(); ringtone = null
        player?.runCatching { stop(); release() }; player = null
        previousMode?.let { audioManager.mode = it }; previousMode = null
    }

    companion object {
        private const val TAG = "FakeCallAudio"
        private const val MAX_BYTES = 15L * 1024 * 1024

        /** Copies a picked recording into app storage; null when it can't be read or is too large. */
        fun copyIn(context: Context, source: String, id: String): String? = runCatching {
            val dir = File(context.filesDir, "voices").apply { mkdirs() }
            val target = File(dir, id.filter { it.isLetterOrDigit() || it == '-' })
            val uri = source.toUri()
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { inp ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024); var total = 0L
                    while (true) {
                        val n = inp.read(buf); if (n < 0) break
                        total += n; if (total > MAX_BYTES) { out.close(); target.delete(); return null }
                        out.write(buf, 0, n)
                    }
                }
            }
            Uri.fromFile(target).toString()
        }.getOrNull()

        fun delete(uri: String) { runCatching { uri.toUri().path?.let { File(it).takeIf { f -> f.path.contains("/voices/") }?.delete() } } }
    }
}
