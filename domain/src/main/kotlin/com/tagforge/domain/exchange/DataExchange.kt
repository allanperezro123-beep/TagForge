package com.tagforge.domain.exchange

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.ports.ExchangeStore
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

data class SectionCount(val imported: Int, val skipped: Int)

data class ImportReport(val history: SectionCount, val templates: SectionCount, val tasks: SectionCount) {
    val imported: Int get() = history.imported + templates.imported + tasks.imported
    val skipped: Int get() = history.skipped + templates.skipped + tasks.skipped
}

/** A finished export: the file content and how many items it holds. */
data class ExportedText(val text: String, val itemCount: Int)

sealed interface ImportFailure {
    /** The file was refused as a whole; nothing was imported. */
    data class Invalid(val error: ImportError) : ImportFailure

    /** The database failed; the transaction was rolled back, nothing was imported. */
    data object StorageFailed : ImportFailure
}

/** Export and import of the user's data (HT-04) on top of an [ExchangeStore]. */
class DataExchange(
    private val store: ExchangeStore,
    private val clock: Clock,
) {

    suspend fun exportJson(): ExportedText {
        val data = store.snapshot()
        return ExportedText(JsonExchange.export(data, clock.now()), data.size)
    }

    /** CSV carries the history only. */
    suspend fun exportCsv(): ExportedText {
        val history = store.snapshot().history
        return ExportedText(CsvExporter.export(history), history.size)
    }

    suspend fun import(text: String): Outcome<ImportReport, ImportFailure> {
        val parsed = when (val result = JsonExchange.parse(text)) {
            is Outcome.Err -> return Outcome.Err(ImportFailure.Invalid(result.error))
            is Outcome.Ok -> result.value
        }
        val added = try {
            store.addMissing(parsed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            return Outcome.Err(ImportFailure.StorageFailed)
        }
        return Outcome.Ok(
            ImportReport(
                history = SectionCount(added.history.size, parsed.history.size - added.history.size),
                templates = SectionCount(added.templates.size, parsed.templates.size - added.templates.size),
                tasks = SectionCount(added.tasks.size, parsed.tasks.size - added.tasks.size),
            ),
        )
    }
}
