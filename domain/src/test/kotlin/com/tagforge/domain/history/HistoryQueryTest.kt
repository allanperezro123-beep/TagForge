package com.tagforge.domain.history

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.InMemoryHistoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** HT-03: search and filters combine with AND; matching is case-insensitive and ignores UID separators. */
class HistoryQueryTest {

    private fun entry(
        at: Long,
        kind: HistoryKind = HistoryKind.READ,
        outcome: HistoryOutcome = HistoryOutcome.SUCCESS,
        summary: String = "entry $at",
        uid: Bytes? = Bytes.of(0x04, 0xA1, 0xB2, 0xC3),
        tagType: String? = "NTAG213",
    ) = HistoryEntry(
        at = Instant.fromEpochMilliseconds(at),
        kind = kind,
        outcome = outcome,
        uid = uid,
        tagType = tagType,
        summary = summary,
        ndef = null,
    )

    // ---- text ---------------------------------------------------------------------------------------------

    @Test
    fun textMatchesTheSummaryIgnoringCase() {
        val query = HistoryQuery(text = "wifi")
        assertTrue(query.matches(entry(1, summary = "WiFi Casa")))
        assertFalse(query.matches(entry(1, summary = "https://example.com")))
    }

    @Test
    fun textMatchesTheUidWhateverTheSeparators() {
        val e = entry(1, uid = Bytes.of(0x04, 0xA1, 0xB2, 0xC3))
        assertTrue(HistoryQuery(text = "a1b2").matches(e))
        assertTrue(HistoryQuery(text = "04:A1:b2").matches(e))
        assertTrue(HistoryQuery(text = "04 a1").matches(e))
        assertFalse(HistoryQuery(text = "04:A1:FF").matches(e))
    }

    @Test
    fun textMatchesTheTagType() {
        assertTrue(HistoryQuery(text = "ntag215").matches(entry(1, tagType = "NTAG215")))
        assertFalse(HistoryQuery(text = "ntag215").matches(entry(1, tagType = "NTAG213")))
    }

    @Test
    fun blankTextMatchesEverythingEvenWithoutUid() {
        assertTrue(HistoryQuery(text = "   ").matches(entry(1, uid = null, tagType = null)))
        assertFalse(HistoryQuery(text = "zzz").matches(entry(1, uid = null, tagType = null)))
    }

    @Test
    fun theUidNeedleStripsSeparatorsAndWhitespace() {
        assertEquals("04A1", HistoryQuery(text = " 04:a1 ").uidNeedle)
        assertEquals("04A1B2", HistoryQuery(text = "04-A1 b2").uidNeedle)
    }

    // ---- kinds / outcome / dates ------------------------------------------------------------------------------

    @Test
    fun noKindsSelectedMeansAllKinds() {
        assertTrue(HistoryQuery().matches(entry(1, kind = HistoryKind.CLONE)))
        assertTrue(HistoryQuery(kinds = setOf(HistoryKind.WRITE, HistoryKind.ERASE)).matches(entry(1, kind = HistoryKind.ERASE)))
        assertFalse(HistoryQuery(kinds = setOf(HistoryKind.WRITE)).matches(entry(1, kind = HistoryKind.READ)))
    }

    @Test
    fun outcomeFilterSeparatesSuccessFromAnyFailure() {
        val ok = entry(1, outcome = HistoryOutcome.SUCCESS)
        val lost = entry(2, outcome = HistoryOutcome.FAILED_TAGLOST)
        val capacity = entry(3, outcome = HistoryOutcome.FAILED_CAPACITY)
        val failed = HistoryQuery(outcome = OutcomeFilter.FAILED)
        assertFalse(failed.matches(ok))
        assertTrue(failed.matches(lost))
        assertTrue(failed.matches(capacity))
        assertTrue(HistoryQuery(outcome = OutcomeFilter.SUCCESS).matches(ok))
        assertFalse(HistoryQuery(outcome = OutcomeFilter.SUCCESS).matches(lost))
    }

    @Test
    fun dateRangeIsInclusiveOnBothEnds() {
        val range = HistoryQuery(from = Instant.fromEpochMilliseconds(1_000), to = Instant.fromEpochMilliseconds(2_000))
        assertFalse(range.matches(entry(999)))
        assertTrue(range.matches(entry(1_000)))
        assertTrue(range.matches(entry(2_000)))
        assertFalse(range.matches(entry(2_001)))
        assertTrue(HistoryQuery(from = Instant.fromEpochMilliseconds(1_000)).matches(entry(9_999_999)))
        assertTrue(HistoryQuery(to = Instant.fromEpochMilliseconds(1_000)).matches(entry(0)))
    }

    @Test
    fun isFilteredIsTrueOnlyWhenSomethingNarrowsTheList() {
        assertFalse(HistoryQuery().isFiltered)
        assertFalse(HistoryQuery(text = "  ").isFiltered)
        assertTrue(HistoryQuery(text = "x").isFiltered)
        assertTrue(HistoryQuery(kinds = setOf(HistoryKind.READ)).isFiltered)
        assertTrue(HistoryQuery(outcome = OutcomeFilter.FAILED).isFiltered)
        assertTrue(HistoryQuery(from = Instant.fromEpochMilliseconds(1)).isFiltered)
    }

    // ---- through the repository (HT-03 "Combined filter") -----------------------------------------------------------

    @Test
    fun tenEntriesWithThreeFailedWritesFilterToExactlyThree() = runTest {
        val repo = InMemoryHistoryRepository()
        repeat(7) { repo.add(entry(it + 1L, kind = HistoryKind.READ)) }
        repeat(3) { repo.add(entry(100L + it, kind = HistoryKind.WRITE, outcome = HistoryOutcome.FAILED_TAGLOST)) }
        repo.add(entry(200, kind = HistoryKind.WRITE, outcome = HistoryOutcome.SUCCESS))

        val failedWrites = repo.observe(HistoryQuery(kinds = setOf(HistoryKind.WRITE), outcome = OutcomeFilter.FAILED)).first()

        assertEquals(listOf(102L, 101L, 100L), failedWrites.map { it.at.toEpochMilliseconds() })
        assertEquals(11, repo.observe().first().size)
    }

    @Test
    fun theLimitAppliesAfterFiltering() = runTest {
        val repo = InMemoryHistoryRepository()
        repeat(5) { repo.add(entry(it + 1L, kind = HistoryKind.WRITE)) }
        repeat(5) { repo.add(entry(100L + it, kind = HistoryKind.READ)) }

        val writes = repo.observe(HistoryQuery(limit = 3, kinds = setOf(HistoryKind.WRITE))).first()

        assertEquals(listOf(5L, 4L, 3L), writes.map { it.at.toEpochMilliseconds() })
    }
}
