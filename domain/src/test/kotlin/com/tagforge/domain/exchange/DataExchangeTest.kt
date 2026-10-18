package com.tagforge.domain.exchange

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FixedClock
import com.tagforge.domain.fakes.InMemoryExchangeStore
import com.tagforge.domain.fakes.InMemoryHistoryRepository
import com.tagforge.domain.fakes.InMemoryTaskRepository
import com.tagforge.domain.fakes.InMemoryTemplateRepository
import com.tagforge.domain.history.HistoryEntry
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryOutcome
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.task.RingerMode
import com.tagforge.domain.task.Task
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.template.Template
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/** HT-04 end to end over fakes: export, wipe, import; duplicates skipped; a bad file or a failing store changes nothing. */
class DataExchangeTest {

    private val history = InMemoryHistoryRepository()
    private val templates = InMemoryTemplateRepository()
    private val tasks = InMemoryTaskRepository()
    private val store = InMemoryExchangeStore(history, templates, tasks)
    private val exchange = DataExchange(store, FixedClock(Instant.fromEpochMilliseconds(5_000)))

    private fun entry(n: Int) = HistoryEntry(
        id = n.toLong(),
        at = Instant.fromEpochMilliseconds(1_000L * n),
        kind = if (n % 2 == 0) HistoryKind.WRITE else HistoryKind.READ,
        outcome = if (n % 7 == 0) HistoryOutcome.FAILED_TAGLOST else HistoryOutcome.SUCCESS,
        uid = Bytes.of(0x04, n, 0xB2, 0xC3),
        tagType = "NTAG215",
        summary = "entry $n",
        ndef = null,
    )

    private fun template(n: Int) = Template(
        id = n.toLong(),
        name = "Template $n",
        records = listOf(RecordSpec.Text("text $n")),
        createdAt = Instant.fromEpochMilliseconds(1),
        updatedAt = Instant.fromEpochMilliseconds(2),
    )

    private suspend fun populate(historyCount: Int = 50, templateCount: Int = 5) {
        (1..historyCount).forEach { history.add(entry(it)) }
        (1..templateCount).forEach { templates.upsert(template(it)) }
    }

    private fun ok(result: Outcome<ImportReport, ImportFailure>): ImportReport {
        assertIs<Outcome.Ok<ImportReport>>(result, "expected success but got $result")
        return result.value
    }

    @Test
    fun exportWipeImportRestoresAllFiftyFiveItemsEqual() = runTest {
        populate()
        tasks.upsert(
            Task(id = 1, name = "Night", actions = listOf(TaskAction.SetRinger(RingerMode.VIBRATE)), createdAt = Instant.fromEpochMilliseconds(1), updatedAt = Instant.fromEpochMilliseconds(2)),
        )
        val before = store.snapshot()
        val file = exchange.exportJson().text

        history.clear()
        templates.observeAll().first().forEach { templates.delete(it.id) }
        tasks.delete(1)
        assertTrue(store.snapshot().isEmpty)

        val report = ok(exchange.import(file))

        assertEquals(SectionCount(imported = 50, skipped = 0), report.history)
        assertEquals(SectionCount(imported = 5, skipped = 0), report.templates)
        assertEquals(SectionCount(imported = 1, skipped = 0), report.tasks)
        assertEquals(before.history.toSet(), store.snapshot().history.toSet())
        assertEquals(before.templates.toSet(), store.snapshot().templates.toSet())
        assertEquals(before.tasks, store.snapshot().tasks)
    }

    @Test
    fun importingTheSameFileTwiceSkipsEverythingTheSecondTime() = runTest {
        populate(historyCount = 4, templateCount = 2)
        val file = exchange.exportJson().text

        val report = ok(exchange.import(file))

        assertEquals(SectionCount(imported = 0, skipped = 4), report.history)
        assertEquals(SectionCount(imported = 0, skipped = 2), report.templates)
        assertEquals(4, store.snapshot().history.size)
    }

    @Test
    fun onlyEntriesWhoseIdIsNewAreAdded() = runTest {
        populate(historyCount = 3, templateCount = 0)
        val file = JsonExchange.export(ExchangeData((1..6).map(::entry), emptyList(), emptyList()), Instant.fromEpochMilliseconds(1))

        val report = ok(exchange.import(file))

        assertEquals(SectionCount(imported = 3, skipped = 3), report.history)
        assertEquals((1L..6L).toSet(), store.snapshot().history.map { it.id }.toSet())
    }

    @Test
    fun aCorruptFileImportsNothingAndNamesTheFailingEntry() = runTest {
        populate(historyCount = 2, templateCount = 0)
        val good = JsonExchange.export(ExchangeData((10..16).map(::entry), emptyList(), emptyList()), Instant.fromEpochMilliseconds(1))
        val corrupt = good.replace("\"id\":16,", "\"id\":\"sixteen\",")   // the 7th entry

        val result = exchange.import(corrupt)

        assertEquals(
            Outcome.Err(ImportFailure.Invalid(ImportError.BadEntry(ExchangeSection.HISTORY, 7, EntryProblem.BAD_STRUCTURE))),
            result,
        )
        assertEquals(2, store.snapshot().history.size)
    }

    @Test
    fun aStorageFailureChangesNothingAndIsReported() = runTest {
        populate(historyCount = 2, templateCount = 1)
        val file = JsonExchange.export(ExchangeData((10..12).map(::entry), listOf(template(9)), emptyList()), Instant.fromEpochMilliseconds(1))
        store.failNextImport = true

        val result = exchange.import(file)

        assertEquals(Outcome.Err(ImportFailure.StorageFailed), result)
        assertEquals(2, store.snapshot().history.size)
        assertEquals(1, store.snapshot().templates.size)
    }

    @Test
    fun theCsvHasTheHistoryOnly() = runTest {
        populate(historyCount = 3, templateCount = 2)
        val lines = exchange.exportCsv().text.trimEnd().split("\r\n")
        assertEquals(4, lines.size)
        assertTrue(lines[1].startsWith("3,"))   // newest first
    }

    @Test
    fun theJsonRecordsWhenItWasExported() = runTest {
        assertTrue("\"exportedAt\":5000" in exchange.exportJson().text)
    }
}
