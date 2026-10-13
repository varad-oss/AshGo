package com.varad.unstoppableash.data.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.varad.unstoppableash.domain.media.ImageBudget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Picked photo → JPEG inside the D-5 budget, always on [Dispatchers.Default] (decoding a 12 MP photo
 * on the main thread would freeze the chat). Honours EXIF rotation so phone photos aren't sideways.
 */
class AndroidPhotoEncoder(
    private val resolver: ContentResolver,
    private val onWorkThread: () -> Unit = {},
) : PhotoEncoder {

    override suspend fun encode(uri: String): EncodedPhoto? = withContext(Dispatchers.Default) {
        onWorkThread()
        try {
            encodeBlocking(Uri.parse(uri))
        } catch (e: Exception) {
            Log.w(TAG, "photo refused: ${e.javaClass.simpleName}") // unreadable, revoked permission…: refuse, never crash
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "photo refused: out of memory")
            null
        }
    }

    private fun encodeBlocking(uri: Uri): EncodedPhoto? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode always returns null; success is outWidth/outHeight being set.
        (resolver.openInputStream(uri) ?: return null).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ImageBudget.MAX_EDGE_PX) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val (w, h) = ImageBudget.scaledSize(decoded.width, decoded.height, ImageBudget.MAX_EDGE_PX)
        val scaled = if (w == decoded.width && h == decoded.height) decoded else Bitmap.createScaledBitmap(decoded, w, h, true)
        val upright = rotate(scaled, rotationOf(uri))

        val encoded = HashMap<Int, ByteArray>()
        fun jpegAt(q: Int) = encoded.getOrPut(q) {
            ByteArrayOutputStream().also { upright.compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray()
        }
        val quality = ImageBudget.chooseQuality({ jpegAt(it).size }, ImageBudget.TARGET_BYTES, ImageBudget.HARD_LIMIT_BYTES) ?: return null
        return EncodedPhoto(jpegAt(quality), upright.width, upright.height)
    }

    private fun rotationOf(uri: Uri): Int = runCatching {
        resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap =
        if (degrees == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)

    private companion object {
        const val TAG = "AshGoPhoto"
    }
}
