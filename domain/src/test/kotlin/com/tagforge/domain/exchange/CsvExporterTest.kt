package com.tagforge.domain.exchange

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.history.HistoryEntry
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** HT-04 CSV: history only, RFC 4180 quoting, UTF-8, and no formula injection from tag content. */
class CsvExporterTest {

    private fun entry(
        summary: String,
        id: Long = 1,
        kind: HistoryKind = HistoryKind.WRITE,
        outcome: HistoryOutcome = HistoryOutcome.SUCCESS,
        uid: Bytes? = Bytes.of(0x04, 0xA1, 0xB2, 0xC3),
        tagType: String? = "NTAG213",
    ) = HistoryEntry(
        id = id,
        at = Instant.fromEpochMilliseconds(1_700_000_000_000),
        kind = kind,
        outcome = outcome,
        uid = uid,
        tagType = tagType,
        summary = summary,
        ndef = Bytes.of(0xD0, 0, 0),
    )

    private fun lines(csv: String) = csv.split("\r\n")

    @Test
    fun theHeaderNamesEveryColumnAndRowsEndWithCrlf() {
        val csv = CsvExporter.export(listOf(entry("hello")))

        assertEquals("id,time,kind,outcome,uid,tag_type,summary", lines(csv)[0])
        assertEquals("1,2023-11-14T22:13:20Z,write,success,04A1B2C3,NTAG213,hello", lines(csv)[1])
        assertTrue(csv.endsWith("\r\n"))
        assertEquals(3, lines(csv).size)   // header, one row, and the empty tail after the final CRLF
    }

    @Test
    fun noEntriesStillProducesTheHeader() {
        assertEquals("id,time,kind,outcome,uid,tag_type,summary\r\n", CsvExporter.export(emptyList()))
    }

    @Test
    fun commasQuotesAndNewlinesInTheSummaryAreQuotedPerRfc4180() {
        val csv = CsvExporter.export(
            listOf(
                entry("a, b", id = 1),
                entry("say \"hi\"", id = 2),
                entry("two\nlines", id = 3),
                entry("carriage\rreturn", id = 4),
            ),
        )
        val body = csv.removePrefix("id,time,kind,outcome,uid,tag_type,summary\r\n")

        assertTrue(body.contains("NTAG213,\"a, b\"\r\n"))
        assertTrue(body.contains("NTAG213,\"say \"\"hi\"\"\"\r\n"))
        assertTrue(body.contains("NTAG213,\"two\nlines\"\r\n"))
        assertTrue(body.contains("NTAG213,\"carriage\rreturn\"\r\n"))
    }

    @Test
    fun plainTextIsLeftUnquotedAndUnicodeSurvivesAsUtf8() {
        val csv = CsvExporter.export(listOf(entry("Café ñandú 日本")))
        assertTrue(csv.contains(",Café ñandú 日本\r\n"))
        // the file the user gets is the UTF-8 encoding of this text, and it decodes back to the same characters
        assertEquals(csv, String(CsvExporter.toUtf8(csv), Charsets.UTF_8))
        assertTrue(CsvExporter.toUtf8(csv).size > csv.length)
    }

    @Test
    fun missingUidAndTypeLeaveEmptyCells() {
        val csv = CsvExporter.export(listOf(entry("x", uid = null, tagType = null, outcome = HistoryOutcome.FAILED_TAGLOST)))
        assertEquals("1,2023-11-14T22:13:20Z,write,failed_taglost,,,x", lines(csv)[1])
    }

    @Test
    fun cellsThatWouldBeReadAsFormulasAreDefused() {
        val csv = CsvExporter.export(
            listOf(
                entry("=HYPERLINK(\"http://evil\")", id = 1),
                entry("+1+1", id = 2),
                entry("-2+3", id = 3),
                entry("@SUM(A1)", id = 4),
                entry("5 - 3", id = 5),
            ),
        )
        val rows = lines(csv)

        assertTrue(rows[1].endsWith(",\"'=HYPERLINK(\"\"http://evil\"\")\""))
        assertTrue(rows[2].endsWith(",'+1+1"))
        assertTrue(rows[3].endsWith(",'-2+3"))
        assertTrue(rows[4].endsWith(",'@SUM(A1)"))
        assertTrue(rows[5].endsWith(",5 - 3"))
    }
}
