package com.tagforge.app.feature.productivity

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.exchange.DataExchange
import com.tagforge.domain.exchange.EntryProblem
import com.tagforge.domain.exchange.ExchangeData
import com.tagforge.domain.exchange.ExchangeSection
import com.tagforge.domain.exchange.ImportError
import com.tagforge.domain.exchange.JsonExchange
import com.tagforge.domain.exchange.SectionCount
import com.tagforge.domain.fakes.FixedClock
import com.tagforge.domain.fakes.InMemoryExchangeStore
import com.tagforge.domain.fakes.InMemoryHistoryRepository
import com.tagforge.domain.fakes.InMemoryTaskRepository
import com.tagforge.domain.fakes.InMemoryTemplateRepository
import com.tagforge.domain.history.HistoryEntry
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryOutcome
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.template.Template
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/** HT-04 screen logic: pick a file, export or import it, say exactly what happened. */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportExportViewModelTest {

    private class FakeDocumentIo : DocumentIo {
        val files = mutableMapOf<String, ByteArray>()
        var failWrites = false
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun write(target: String, data: ByteArray): Boolean {
            gate?.await()
            if (failWrites) return false
            files[target] = data
            return true
        }

        override suspend fun read(source: String, maxBytes: Int): DocumentRead {
            gate?.await()
            val bytes = files[source] ?: return DocumentRead.Unreadable
            return if (bytes.size > maxBytes) DocumentRead.TooLarge else DocumentRead.Bytes(bytes)
        }
    }

    private val history = InMemoryHistoryRepository()
    private val templates = InMemoryTemplateRepository()
    private val store = InMemoryExchangeStore(history, templates, InMemoryTaskRepository())
    private val io = FakeDocumentIo()
    private val clock = FixedClock(Instant.parse("2026-10-17T12:00:00Z"))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = ImportExportViewModel(DataExchange(store, clock), io, clock)

    private fun entry(n: Int) = HistoryEntry(
        id = n.toLong(),
        at = Instant.fromEpochMilliseconds(1_000L * n),
        kind = HistoryKind.READ,
        outcome = HistoryOutcome.SUCCESS,
        uid = Bytes.of(4, n, 2, 3),
        tagType = "NTAG213",
        summary = "entry $n",
        ndef = null,
    )

    private suspend fun populate(historyCount: Int = 50, templateCount: Int = 5) {
        (1..historyCount).forEach { history.add(entry(it)) }
        (1..templateCount).forEach {
            templates.upsert(Template(name = "T$it", records = listOf(RecordSpec.Text("x")), createdAt = Instant.fromEpochMilliseconds(1), updatedAt = Instant.fromEpochMilliseconds(1)))
        }
    }

    // ---- file names -------------------------------------------------------------------------------------------------

    @Test
    fun suggestedFileNamesCarryTheDateAndTheRightExtension() {
        val vm = vm()
        assertEquals("tagforge-backup-2026-10-17.json", vm.suggestedFileName(ExportFormat.JSON))
        assertEquals("tagforge-history-2026-10-17.csv", vm.suggestedFileName(ExportFormat.CSV))
        assertEquals("application/json", ExportFormat.JSON.mimeType)
        assertEquals("text/csv", ExportFormat.CSV.mimeType)
    }

    // ---- export -----------------------------------------------------------------------------------------------------

    @Test
    fun exportingJsonWritesTheBackupAndReportsTheCount() = runTest {
        populate()
        val vm = vm()

        vm.onExport(ExportFormat.JSON, "content://picked/backup")

        val written = String(io.files.getValue("content://picked/backup"), Charsets.UTF_8)
        assertTrue("\"schema\":1" in written)
        assertEquals(ImportExportMessage.Exported(ExportFormat.JSON, itemCount = 55), vm.uiState.value.message)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun exportingCsvWritesHistoryRowsOnly() = runTest {
        populate(historyCount = 3, templateCount = 2)
        val vm = vm()

        vm.onExport(ExportFormat.CSV, "content://picked/history")

        val lines = String(io.files.getValue("content://picked/history"), Charsets.UTF_8).trimEnd().split("\r\n")
        assertEquals("id,time,kind,outcome,uid,tag_type,summary", lines.first())
        assertEquals(4, lines.size)
        assertEquals(ImportExportMessage.Exported(ExportFormat.CSV, itemCount = 3), vm.uiState.value.message)
    }

    @Test
    fun aFailedWriteIsReportedAndLeavesNoFile() = runTest {
        populate(historyCount = 1, templateCount = 0)
        io.failWrites = true
        val vm = vm()

        vm.onExport(ExportFormat.JSON, "content://picked/backup")

        assertEquals(ImportExportMessage.ExportFailed, vm.uiState.value.message)
        assertTrue(io.files.isEmpty())
    }

    // ---- import -----------------------------------------------------------------------------------------------------

    @Test
    fun importingABackupRestoresAndReportsEachSectionSeparately() = runTest {
        populate()
        val vm = vm()
        vm.onExport(ExportFormat.JSON, "content://file")
        history.clear()

        vm.onImport("content://file")

        val message = vm.uiState.value.message
        assertIs<ImportExportMessage.Imported>(message)
        assertEquals(SectionCount(50, 0), message.report.history)
        assertEquals(SectionCount(0, 5), message.report.templates)   // the templates were never wiped
        assertEquals(50, store.snapshot().history.size)
    }

    @Test
    fun aMalformedEntryRejectsTheWholeFileAndNamesIt() = runTest {
        val good = JsonExchange.export(ExchangeData((1..9).map(::entry), emptyList(), emptyList()), Instant.fromEpochMilliseconds(1))
        io.files["content://bad"] = good.replace("\"id\":7,", "\"id\":\"seven\",").toByteArray()
        val vm = vm()

        vm.onImport("content://bad")

        assertEquals(
            ImportExportMessage.ImportRejected(ImportError.BadEntry(ExchangeSection.HISTORY, 7, EntryProblem.BAD_STRUCTURE)),
            vm.uiState.value.message,
        )
        assertTrue(store.snapshot().isEmpty)
    }

    @Test
    fun aNewerSchemaIsRejectedWithItsVersion() = runTest {
        io.files["content://new"] = """{"schema":2,"history":[]}""".toByteArray()
        val vm = vm()
        vm.onImport("content://new")
        assertEquals(ImportExportMessage.ImportRejected(ImportError.UnsupportedSchema(2)), vm.uiState.value.message)
    }

    @Test
    fun aStorageFailureIsReportedAndNothingIsImported() = runTest {
        io.files["content://f"] = JsonExchange.export(ExchangeData(listOf(entry(1)), emptyList(), emptyList()), Instant.fromEpochMilliseconds(1)).toByteArray()
        store.failNextImport = true
        val vm = vm()

        vm.onImport("content://f")

        assertEquals(ImportExportMessage.ImportStorageFailed, vm.uiState.value.message)
        assertTrue(store.snapshot().isEmpty)
    }

    @Test
    fun filesThatAreTooBigUnreadableOrNotTextNeverReachTheParser() = runTest {
        val vm = vm()
        vm.onImport("content://missing")
        assertEquals(ImportExportMessage.ImportUnreadable, vm.uiState.value.message)

        io.files["content://huge"] = ByteArray(ImportExportViewModel.MAX_IMPORT_BYTES + 1)
        vm.onImport("content://huge")
        assertEquals(ImportExportMessage.ImportTooLarge, vm.uiState.value.message)

        io.files["content://binary"] = byteArrayOf(0x7B, 0xC3.toByte(), 0x28, 0x7D)   // invalid UTF-8
        vm.onImport("content://binary")
        assertEquals(ImportExportMessage.ImportUnreadable, vm.uiState.value.message)
    }

    // ---- progress and dismissal -----------------------------------------------------------------------------------

    @Test
    fun busyWhileTheFileIsBeingHandledAndFreeAfterwards() = runTest {
        populate(historyCount = 2, templateCount = 0)
        io.gate = CompletableDeferred()
        val vm = vm()

        vm.onExport(ExportFormat.JSON, "content://slow")
        assertTrue(vm.uiState.value.busy)
        assertEquals(null, vm.uiState.value.message)

        io.gate!!.complete(Unit)
        assertFalse(vm.uiState.value.busy)
        assertIs<ImportExportMessage.Exported>(vm.uiState.value.message)
    }

    @Test
    fun dismissingTheMessageClearsIt() = runTest {
        populate(historyCount = 1, templateCount = 0)
        val vm = vm()
        vm.onExport(ExportFormat.CSV, "content://x")
        vm.onMessageShown()
        assertEquals(null, vm.uiState.value.message)
    }
}
