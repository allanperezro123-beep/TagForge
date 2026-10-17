package com.tagforge.app.infrastructure.android

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.tools.DumpFormats
import com.tagforge.domain.tools.DumpReport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Naming and path rules for exported files (pure, unit tested). */
object ExportFiles {
    const val TEXT = "txt"
    const val BINARY = "bin"
    const val SUBFOLDER = "exports"

    private val EXTENSIONS = setOf(TEXT, BINARY)
    private val UNSAFE = Regex("[^A-Za-z0-9._-]")
    private const val UID_TAIL_BYTES = 2

    /** `tagforge-dump-NTAG213-6E80-20261017-120000.txt`: chip, last two UID bytes (not the whole UID) and UTC time. */
    fun fileName(chip: String?, uid: Bytes, extension: String, epochMillis: Long): String {
        require(extension in EXTENSIONS) { "Unsupported export extension: $extension" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(epochMillis))
        val tail = uid.slice(maxOf(0, uid.size - UID_TAIL_BYTES)).toHex()
        return sanitize("tagforge-dump-${chip ?: "tag"}-$tail-$stamp.$extension")
    }

    fun sanitize(name: String): String = name.replace(UNSAFE, "_")

    /** The file for [name] inside [dir]; refuses anything that is not a plain file name directly in [dir]. */
    fun resolveInside(dir: File, name: String): File {
        require(name == sanitize(name) && name.isNotEmpty() && name != "." && name != "..") { "Not a plain file name: $name" }
        val file = File(dir, name)
        require(file.canonicalFile.parentFile == dir.canonicalFile) { "Path escapes the export folder: $name" }
        return file
    }

    /** Deletes exports older than [maxAgeMillis]; returns how many were removed. */
    fun sweep(dir: File, nowMillis: Long, maxAgeMillis: Long): Int =
        dir.listFiles().orEmpty().count { it.isFile && nowMillis - it.lastModified() > maxAgeMillis && it.delete() }
}

/**
 * Hands a dump to the system share sheet. The file lives in a private cache sub-folder and is exposed through a
 * FileProvider URI with a temporary read grant: it is never written to shared storage and no permission is needed.
 */
class ShareExporter(private val context: Context) {

    private val authority = "${context.packageName}.files"

    fun shareText(report: DumpReport, nowMillis: Long = System.currentTimeMillis()) {
        val name = ExportFiles.fileName(report.dump.model?.displayName, report.info.uid, ExportFiles.TEXT, nowMillis)
        share(name, "text/plain", DumpFormats.toText(report).toByteArray(Charsets.UTF_8), nowMillis)
    }

    fun shareBinary(report: DumpReport, nowMillis: Long = System.currentTimeMillis()) {
        val name = ExportFiles.fileName(report.dump.model?.displayName, report.info.uid, ExportFiles.BINARY, nowMillis)
        share(name, "application/octet-stream", DumpFormats.toBinary(report.dump).toByteArray(), nowMillis)
    }

    private fun share(name: String, mimeType: String, content: ByteArray, nowMillis: Long) {
        val dir = File(context.cacheDir, ExportFiles.SUBFOLDER).apply { mkdirs() }
        ExportFiles.sweep(dir, nowMillis, MAX_AGE_MILLIS)
        val file = ExportFiles.resolveInside(dir, name).apply { writeBytes(content) }
        val uri = FileProvider.getUriForFile(context, authority, file)

        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private companion object {
        const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
    }
}
