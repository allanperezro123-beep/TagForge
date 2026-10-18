package com.tagforge.app.feature.productivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.exchange.DataExchange
import com.tagforge.domain.exchange.ExportedText
import com.tagforge.domain.exchange.ImportError
import com.tagforge.domain.exchange.ImportFailure
import com.tagforge.domain.exchange.ImportReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.time.ZoneOffset
import kotlin.time.Clock

enum class ExportFormat(val mimeType: String, val extension: String, val namePrefix: String) {
    JSON("application/json", "json", "tagforge-backup"),
    CSV("text/csv", "csv", "tagforge-history"),
}

/** What happened last. The screen turns each into a sentence; the ViewModel never builds text. */
sealed interface ImportExportMessage {
    data class Exported(val format: ExportFormat, val itemCount: Int) : ImportExportMessage
    data object ExportFailed : ImportExportMessage
    data class Imported(val report: ImportReport) : ImportExportMessage
    data class ImportRejected(val error: ImportError) : ImportExportMessage
    data object ImportStorageFailed : ImportExportMessage
    data object ImportTooLarge : ImportExportMessage
    data object ImportUnreadable : ImportExportMessage
}

data class ImportExportUiState(
    val busy: Boolean = false,
    val message: ImportExportMessage? = null,
)

/**
 * Export and import of the user's data (HT-04) through files picked with the Storage Access Framework: the screen
 * launches the system picker and hands the chosen URI string here. An import either applies completely or not at all.
 */
class ImportExportViewModel(
    private val exchange: DataExchange,
    private val documents: DocumentIo,
    private val clock: Clock,
) : ViewModel() {

    private val state = MutableStateFlow(ImportExportUiState())
    val uiState: StateFlow<ImportExportUiState> = state.asStateFlow()

    /** The name the picker proposes, e.g. `tagforge-backup-2026-10-17.json`. */
    fun suggestedFileName(format: ExportFormat): String {
        val date = java.time.Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(ZoneOffset.UTC).toLocalDate()
        return "${format.namePrefix}-$date.${format.extension}"
    }

    fun onExport(format: ExportFormat, target: String) = launchBusy {
        val exported: ExportedText = when (format) {
            ExportFormat.JSON -> exchange.exportJson()
            ExportFormat.CSV -> exchange.exportCsv()
        }
        val written = documents.write(target, exported.text.toByteArray(Charsets.UTF_8))
        state.update {
            it.copy(
                message = if (written) ImportExportMessage.Exported(format, exported.itemCount) else ImportExportMessage.ExportFailed,
            )
        }
    }

    fun onImport(source: String) = launchBusy {
        val message = when (val read = documents.read(source, MAX_IMPORT_BYTES)) {
            DocumentRead.TooLarge -> ImportExportMessage.ImportTooLarge
            DocumentRead.Unreadable -> ImportExportMessage.ImportUnreadable
            is DocumentRead.Bytes -> importText(strictUtf8(read.data))
        }
        state.update { it.copy(message = message) }
    }

    fun onMessageShown() = state.update { it.copy(message = null) }

    private suspend fun importText(text: String?): ImportExportMessage {
        if (text == null) return ImportExportMessage.ImportUnreadable
        return when (val result = exchange.import(text)) {
            is Outcome.Ok -> ImportExportMessage.Imported(result.value)
            is Outcome.Err -> when (val failure = result.error) {
                is ImportFailure.Invalid -> ImportExportMessage.ImportRejected(failure.error)
                ImportFailure.StorageFailed -> ImportExportMessage.ImportStorageFailed
            }
        }
    }

    /** Malformed UTF-8 is an error, not a replacement character: a corrupted file must not half-import. */
    private fun strictUtf8(data: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data)).toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun launchBusy(block: suspend () -> Unit) {
        state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        /** Real backups are a few hundred KB; anything near this is not one of ours. */
        const val MAX_IMPORT_BYTES = 16 * 1024 * 1024
    }
}
