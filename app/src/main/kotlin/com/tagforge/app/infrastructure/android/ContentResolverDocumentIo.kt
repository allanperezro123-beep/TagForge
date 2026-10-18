package com.tagforge.app.infrastructure.android

import android.content.ContentResolver
import android.net.Uri
import com.tagforge.app.feature.productivity.DocumentIo
import com.tagforge.app.feature.productivity.DocumentRead
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * [DocumentIo] over `ContentResolver`. The URIs come from the system file picker, which grants access to that one
 * document: no storage permission exists anywhere in this app. All I/O runs on [io].
 */
class ContentResolverDocumentIo(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher,
) : DocumentIo {

    override suspend fun write(target: String, data: ByteArray): Boolean = withContext(io) {
        try {
            // "wt" truncates: overwriting an existing, longer file must not leave its old tail behind.
            resolver.openOutputStream(Uri.parse(target), "wt")?.use { it.write(data) } != null
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    override suspend fun read(source: String, maxBytes: Int): DocumentRead = withContext(io) {
        try {
            val stream = resolver.openInputStream(Uri.parse(source)) ?: return@withContext DocumentRead.Unreadable
            stream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) return@withContext DocumentRead.TooLarge
                    out.write(buffer, 0, count)
                }
                DocumentRead.Bytes(out.toByteArray())
            }
        } catch (_: IOException) {
            DocumentRead.Unreadable
        } catch (_: SecurityException) {
            DocumentRead.Unreadable
        } catch (_: IllegalArgumentException) {
            DocumentRead.Unreadable
        }
    }

    private companion object {
        const val BUFFER = 16 * 1024
    }
}
