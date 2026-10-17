package com.tagforge.domain.history

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.RecordSpecEncoder
import com.tagforge.domain.tag.TagError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** HT-01: summaries describe the content, and passwords NEVER reach history. */
class HistorySummarizerTest {

    private fun message(vararg specs: RecordSpec): NdefMessage = RecordSpecEncoder.encodeMessage(specs.toList(), null)!!

    @Test
    fun textAndUriSummariesAreTheirContent() {
        assertEquals("hola", HistorySummarizer.summarize(message(RecordSpec.Text("hola", "es"))))
        assertEquals("https://example.com", HistorySummarizer.summarize(message(RecordSpec.Uri("https://example.com"))))
    }

    @Test
    fun otherRecordTypesSummariseTheirMostUsefulField() {
        assertEquals("Ana", HistorySummarizer.summarize(message(RecordSpec.Contact(name = "Ana", phone = "+57300"))))
        assertEquals("Speaker", HistorySummarizer.summarize(message(RecordSpec.Bluetooth("AA:BB:CC:DD:EE:FF", "Speaker"))))
        assertEquals("AA:BB:CC:DD:EE:FF", HistorySummarizer.summarize(message(RecordSpec.Bluetooth("AA:BB:CC:DD:EE:FF"))))
        assertEquals("com.tagforge.app", HistorySummarizer.summarize(message(RecordSpec.AppLaunch("com.tagforge.app"))))
        assertEquals("text/plain", HistorySummarizer.summarize(message(RecordSpec.Mime("text/plain", "hello"))))
    }

    @Test
    fun wifiSummaryIsTheSsidAndNeverThePassword() {
        val summary = HistorySummarizer.summarize(message(RecordSpec.Wifi("Home", "pass1234")))
        assertEquals("Home", summary)
        assertFalse("pass1234" in summary)
    }

    @Test
    fun severalRecordsAreJoinedInOrder() {
        val summary = HistorySummarizer.summarize(message(RecordSpec.Text("hi"), RecordSpec.Uri("https://example.com")))
        assertEquals("hi · https://example.com", summary)
    }

    @Test
    fun longSummariesAreTruncatedWithAnEllipsis() {
        val summary = HistorySummarizer.summarize(message(RecordSpec.Text("a".repeat(300))))
        assertEquals(HistorySummarizer.MAX_LENGTH, summary.length)
        assertTrue(summary.endsWith("…"))
    }

    @Test
    fun nullOrEmptyMessagesHaveAnEmptySummary() {
        assertEquals("", HistorySummarizer.summarize(null))
        assertEquals("", HistorySummarizer.summarize(NdefMessage.EMPTY))
    }

    @Test
    fun unrecognisedRecordsShowAQuestionMark() {
        val unknown = NdefRecord(com.tagforge.domain.ndef.Tnf.UNKNOWN, Bytes.EMPTY, payload = Bytes.of(1, 2))
        assertEquals("?", HistorySummarizer.summarize(NdefMessage(listOf(unknown))))
    }

    // ---- redaction of the stored NDEF bytes ---------------------------------------------------------------

    @Test
    fun redactedWifiBytesDoNotContainThePassword() {
        val original = message(RecordSpec.Wifi("Home", "pass1234"))
        val originalBytes = DefaultNdefCodec.encode(original).toHex()
        val redactedBytes = DefaultNdefCodec.encode(HistorySummarizer.redact(original)).toHex()

        val passwordHex = Bytes.copyOf("pass1234".toByteArray()).toHex()
        assertTrue(passwordHex in originalBytes, "sanity: the unredacted message carries the key")
        assertFalse(passwordHex in redactedBytes)
    }

    @Test
    fun redactionKeepsTheSsidAndLeavesOtherRecordsUntouched() {
        val text = NdefRecord.text("hi", "en")
        val redacted = HistorySummarizer.redact(NdefMessage(listOf(text) + message(RecordSpec.Wifi("Home", "pass1234")).records))
        assertEquals(text, redacted.records[0])
        assertEquals("hi · Home", HistorySummarizer.summarize(redacted))
        assertEquals(2, redacted.records.size)
    }

    // ---- outcomes -------------------------------------------------------------------------------------------

    @Test
    fun tagErrorsMapToStableOutcomes() {
        assertEquals(HistoryOutcome.FAILED_TAGLOST, HistoryOutcome.of(TagError.TagLost))
        assertEquals(HistoryOutcome.FAILED_READ_ONLY, HistoryOutcome.of(TagError.ReadOnly))
        assertEquals(HistoryOutcome.FAILED_CAPACITY, HistoryOutcome.of(TagError.CapacityExceeded(10, 5)))
        assertEquals(HistoryOutcome.FAILED_VERIFY, HistoryOutcome.of(TagError.VerifyMismatch(Bytes.EMPTY, null)))
        assertEquals(HistoryOutcome.FAILED_OTHER, HistoryOutcome.of(TagError.Io("x")))
        assertEquals(HistoryOutcome.SUCCESS, HistoryOutcome.of(null))
    }

    @Test
    fun codesAreStableAndRoundTrip() {
        for (outcome in HistoryOutcome.entries) assertEquals(outcome, HistoryOutcome.fromCode(outcome.code))
        for (kind in HistoryKind.entries) assertEquals(kind, HistoryKind.fromCode(kind.code))
        assertEquals("failed_taglost", HistoryOutcome.FAILED_TAGLOST.code)
        assertEquals("read", HistoryKind.READ.code)
        assertEquals(HistoryOutcome.FAILED_OTHER, HistoryOutcome.fromCode("from-the-future"))
    }
}
