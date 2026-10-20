package com.tagforge.app.infrastructure.android

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Turns a [QrMatrix] into a PNG and puts it where the user can use it: the share sheet ([ShareExporter.sharePng]) or the
 * gallery. Saving needs no permission from Android 10 on (MediaStore owns the file); older phones only get Share.
 */
class QrImageExporter(private val context: Context, private val io: CoroutineDispatcher) {

    /** Black modules on white with a quiet zone, about [targetPx] pixels wide, in whole-pixel modules so it stays sharp. */
    suspend fun render(matrix: QrMatrix, targetPx: Int = TARGET_PX): ByteArray = withContext(io) {
        val modules = matrix.size + 2 * QrEncoder.DEFAULT_QUIET_MODULES
        val image = QrEncoder.toPixels(matrix, scale = (targetPx / modules).coerceAtLeast(1))
        val bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }.toByteArray()
    }

    /** True when the picture was saved to Pictures/Tag Forge. Always false below Android 10, where only sharing is offered. */
    suspend fun saveToGallery(png: ByteArray, nowMillis: Long = System.currentTimeMillis()): Boolean = withContext(io) {
        if (!canSaveToGallery) return@withContext false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, ExportFiles.qrFileName(nowMillis))
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Tag Forge")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        try {
            resolver.openOutputStream(uri)?.use { it.write(png) } ?: throw IOException("no output stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        } catch (_: IOException) {
            resolver.delete(uri, null, null)
            false
        }
    }

    companion object {
        private const val TARGET_PX = 1024
        private const val PNG_QUALITY = 100

        /** Android 10 introduced scoped storage; before it a public save would need the storage permission we do not ask for. */
        val canSaveToGallery: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
}
