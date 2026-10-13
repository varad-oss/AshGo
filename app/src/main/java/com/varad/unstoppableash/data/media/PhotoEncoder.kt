package com.varad.unstoppableash.data.media

/** A photo already shrunk to [com.varad.unstoppableash.domain.media.ImageBudget]. */
class EncodedPhoto(val jpeg: ByteArray, val width: Int, val height: Int)

fun interface PhotoEncoder {
    /** Null when the input can't be read or can't be made small enough (D-5): the caller tells the person. */
    suspend fun encode(uri: String): EncodedPhoto?
}
