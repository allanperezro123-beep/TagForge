package com.tagforge.app.infrastructure.android

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.tagforge.app.feature.productivity.QrImageSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Opens the image returned by the Photo Picker. The URI grants read access to that one image, so no storage
 * permission is needed. Large photos are sampled down: a QR code survives it and memory stays bounded.
 */
class ContentResolverQrImageSource(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher,
) : QrImageSource {

    override suspend fun load(uri: String): QrImage? = withContext(io) {
        try {
            val target = Uri.parse(uri)
            val sample = sampleSize(target) ?: return@withContext null
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(target)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@withContext null
            bitmap.toQrImage()
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun sampleSize(uri: Uri): Int? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / sample > MAX_SIDE_PX) sample *= 2
        return sample
    }

    private fun Bitmap.toQrImage(): QrImage {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return QrImage(pixels, width, height)
    }

    private companion object {
        const val MAX_SIDE_PX = 1600
    }
}
