package com.varad.unstoppableash.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.varad.unstoppableash.domain.media.ImageBudget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class AndroidPhotoEncoderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun write(bitmap: Bitmap, name: String, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG): Uri {
        val f = File(context.cacheDir, name)
        f.outputStream().use { bitmap.compress(format, 100, it) }
        return Uri.fromFile(f)
    }

    @Test fun bigPhotoIsShrunkInsideTheBudget() = runBlocking {
        val bmp = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawRect(0f, 0f, 4000f, 3000f, Paint().apply { shader = LinearGradient(0f, 0f, 4000f, 3000f, 0xFFF6DDE8.toInt(), 0xFF8B405F.toInt(), Shader.TileMode.CLAMP) })
        val out = AndroidPhotoEncoder(context.contentResolver).encode(write(bmp, "big.jpg", Bitmap.CompressFormat.JPEG).toString())
        assertNotNull(out); out!!
        assertTrue(out.jpeg.size <= ImageBudget.HARD_LIMIT_BYTES)
        assertEquals(1024, maxOf(out.width, out.height))
        val decoded = BitmapFactory.decodeByteArray(out.jpeg, 0, out.jpeg.size)
        assertEquals(out.width, decoded.width); assertEquals(out.height, decoded.height)
    }

    @Test fun incompressibleNoiseIsRefused() = runBlocking {
        val r = Random(7)
        val bmp = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        bmp.setPixels(IntArray(1024 * 1024) { r.nextInt() or 0xFF000000.toInt() }, 0, 1024, 0, 0, 1024, 1024)
        assertNull(AndroidPhotoEncoder(context.contentResolver).encode(write(bmp, "noise.png").toString()))
    }

    @Test fun unreadableInputIsRefusedNotThrown() = runBlocking {
        val f = File(context.cacheDir, "junk.jpg").apply { writeText("not an image") }
        assertNull(AndroidPhotoEncoder(context.contentResolver).encode(Uri.fromFile(f).toString()))
    }

    @Test fun encodingRunsOffTheMainThread() {
        var onMain: Boolean? = null
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val uri = write(bmp, "small.png").toString()
        val encoder = AndroidPhotoEncoder(context.contentResolver, onWorkThread = { onMain = Looper.myLooper() == Looper.getMainLooper() })
        runBlocking { withContext(Dispatchers.Main) { encoder.encode(uri) } } // called from the UI thread, like a click handler

        assertEquals(false, onMain)
    }
}
