package com.tagforge.domain.ndef

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** NC-06 robustness and NC-03 parse-side scenarios. */
class NdefParseTest {

    private val codec: NdefCodec = DefaultNdefCodec

    private fun decode(hex: String): Outcome<NdefMessage, NdefParseError> = codec.decode(hex.hex())

    private fun ok(hex: String): NdefMessage {
        val result = decode(hex)
        assertIs<Outcome.Ok<NdefMessage>>(result, "expected Ok for '$hex' but was $result")
        return result.value
    }

    private fun err(hex: String): NdefParseError {
        val result = decode(hex)
        assertIs<Outcome.Err<NdefParseError>>(result, "expected Err for '$hex' but was $result")
        return result.error
    }

    // ---- basics -----------------------------------------------------------------------------

    @Test
    fun parsesSingleTextRecord() {
        val message = ok("D1 01 05 54 02 65 6E 68 69")
        assertEquals(listOf(NdefRecord.text("hi", "en")), message.records)
    }

    @Test
    fun parsesTwoRecordsInOrder() {
        val message = ok("91 01 05 54 02 65 6E 68 69 51 01 0C 55 04 65 78 61 6D 70 6C 65 2E 63 6F 6D")
        assertEquals(listOf(NdefRecord.text("hi", "en"), NdefRecord.uri("https://example.com")), message.records)
    }

    @Test
    fun emptyInputIsAnEmptyMessage() {
        assertEquals(NdefMessage.EMPTY, ok(""))
    }

    @Test
    fun idFieldIsParsedWhenIlIsSet() {
        val record = ok("D9 01 05 01 54 31 02 65 6E 68 69").records.single()
        assertEquals("31".hex(), record.id)
        assertEquals("54".hex(), record.type)
        assertEquals("02 65 6E 68 69".hex(), record.payload)
    }

    @Test
    fun parsesFourByteLengthRecords() {
        val payload = "02 65 6E".hex() + Bytes.copyOf(ByteArray(253) { 'a'.code.toByte() })
        val bytes = "C1 01 00 00 01 00 54".hex() + payload
        val record = (codec.decode(bytes) as Outcome.Ok).value.records.single()
        assertEquals(256, record.payload.size)
    }

    // ---- errors -------------------------------------------------------------------------------

    @Test
    fun truncatedPayloadFailsWithTruncated() {
        assertEquals(NdefParseError.Truncated, err("D1 01 05 54 02 65"))
    }

    @Test
    fun truncatedHeaderFailsWithTruncated() {
        assertEquals(NdefParseError.Truncated, err("D1"))
        assertEquals(NdefParseError.Truncated, err("D1 01"))
        assertEquals(NdefParseError.Truncated, err("C1 01 00 00"))   // long form, length cut
        assertEquals(NdefParseError.Truncated, err("D9 01 05"))      // IL set, id length missing
    }

    @Test
    fun reservedTnfSevenFailsWithInvalidTnf() {
        assertEquals(NdefParseError.InvalidTnf(7), err("D7 00 00"))
    }

    @Test
    fun missingMessageBeginIsReported() {
        assertEquals(NdefParseError.MissingMessageBegin, err("51 01 05 54 02 65 6E 68 69"))
    }

    @Test
    fun missingMessageEndIsReported() {
        assertEquals(NdefParseError.MissingMessageEnd, err("91 01 05 54 02 65 6E 68 69"))
    }

    @Test
    fun continuationWithoutStartIsInvalidChunk() {
        assertEquals(NdefParseError.InvalidChunk, err("D6 00 00"))
    }

    @Test
    fun newRecordInsideAnOpenChunkIsInvalidChunk() {
        // chunk start (CF) followed by a normal record instead of a TNF=6 continuation
        assertEquals(NdefParseError.InvalidChunk, err("B1 01 02 54 02 65 51 01 01 54 00"))
    }

    // ---- chunking -----------------------------------------------------------------------------

    @Test
    fun chunkedRecordIsReassembled() {
        val message = ok("B1 01 02 54 02 65 56 00 03 6E 68 69")
        val record = message.records.single()
        assertEquals(Tnf.WELL_KNOWN, record.tnf)
        assertEquals("54".hex(), record.type)
        assertEquals("02 65 6E 68 69".hex(), record.payload)
        val parsed = RecordInterpreter.interpret(record) as ParsedRecord.Text
        assertEquals("hi", parsed.text)
        assertEquals("en", parsed.languageCode)
    }

    @Test
    fun threeChunksWithAMiddleContinuationAreReassembled() {
        // start (MB|CF|SR|TNF1) + middle (CF|SR|TNF6) + last (ME|SR|TNF6)
        val message = ok("B1 01 02 54 02 65 36 00 01 6E 56 00 02 68 69")
        assertEquals("02 65 6E 68 69".hex(), message.records.single().payload)
    }

    // ---- NC-03 Text / URI decode ----------------------------------------------------------------

    @Test
    fun utf16TextWithoutBomDefaultsToBigEndian() {
        val record = ok("D1 01 07 54 82 65 6E 00 68 00 69").records.single()
        val parsed = RecordInterpreter.interpret(record)
        assertEquals("hi", (parsed as ParsedRecord.Text).text)
        assertEquals("en", parsed.languageCode)
    }

    @Test
    fun utf16TextHonoursLittleEndianBom() {
        val record = ok("D1 01 09 54 82 65 6E FF FE 68 00 69 00").records.single()
        assertEquals("hi", (RecordInterpreter.interpret(record) as ParsedRecord.Text).text)
    }

    @Test
    fun utf8TextDecodesWithItsLanguage() {
        val parsed = RecordInterpreter.interpret(ok("D1 01 07 54 02 65 73 68 6F 6C 61").records.single())
        assertEquals(ParsedRecord.Text(NdefRecord.text("hola", "es"), "es", "hola"), parsed)
    }

    @Test
    fun malformedTextPayloadFallsBackToUnknown() {
        // status says 5 language bytes but only 2 follow
        val record = NdefRecord(Tnf.WELL_KNOWN, NdefRecord.TYPE_TEXT, payload = "05 65 6E".hex())
        assertEquals(ParsedRecord.Unknown(record), RecordInterpreter.interpret(record))
    }

    @Test
    fun knownUriPrefixIsExpanded() {
        val parsed = RecordInterpreter.interpret(ok("D1 01 0C 55 04 65 78 61 6D 70 6C 65 2E 63 6F 6D").records.single())
        assertEquals("https://example.com", (parsed as ParsedRecord.Uri).uri)
        assertEquals(true, parsed.prefixKnown)
    }

    @Test
    fun unknownUriPrefixIsFlaggedAndKeptRaw() {
        val record = ok("D1 01 03 55 7F 61 62").records.single()
        val parsed = RecordInterpreter.interpret(record) as ParsedRecord.Uri
        assertEquals(false, parsed.prefixKnown)
        assertEquals("ab", parsed.uri)
        assertEquals("7F 61 62".hex(), parsed.record.payload)   // raw payload preserved
    }
}
