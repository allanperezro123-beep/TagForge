package com.tagforge.domain.ndef

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** P1 subset of TS-04: Text, URI, generic MIME, External, Unknown (rich decoders arrive in Phase 2). */
class RecordInterpreterTest {

    private fun utf8(text: String): Bytes = Bytes.copyOf(text.toByteArray())

    @Test
    fun interpretsTextAndUriBuilders() {
        val text = RecordInterpreter.interpret(NdefRecord.text("hola", "es"))
        assertEquals(ParsedRecord.Text(NdefRecord.text("hola", "es"), "es", "hola"), text)

        val uri = RecordInterpreter.interpret(NdefRecord.uri("tel:+573001234567"))
        assertEquals("tel:+573001234567", (uri as ParsedRecord.Uri).uri)
    }

    @Test
    fun interpretsGenericMime() {
        val record = NdefRecord(Tnf.MIME, utf8("text/plain"), payload = utf8("hello"))
        val parsed = RecordInterpreter.interpret(record)
        assertIs<ParsedRecord.Mime>(parsed)
        assertEquals("text/plain", parsed.mimeType)
        assertEquals(utf8("hello"), parsed.payload)
    }

    @Test
    fun interpretsExternalTypeSplitAtFirstColon() {
        val record = NdefRecord(Tnf.EXTERNAL, utf8("android.com:pkg"), payload = utf8("com.a"))
        val parsed = RecordInterpreter.interpret(record)
        assertIs<ParsedRecord.External>(parsed)
        assertEquals("android.com", parsed.domain)
        assertEquals("pkg", parsed.type)
        assertEquals(utf8("com.a"), parsed.payload)
    }

    @Test
    fun externalTypeWithoutColonIsUnknown() {
        val record = NdefRecord(Tnf.EXTERNAL, utf8("nocolon"), payload = Bytes.EMPTY)
        assertEquals(ParsedRecord.Unknown(record), RecordInterpreter.interpret(record))
    }

    @Test
    fun absoluteUriRecordCarriesTheUriInTheTypeField() {
        val record = NdefRecord(Tnf.ABSOLUTE_URI, utf8("https://example.com/x"), payload = Bytes.EMPTY)
        val parsed = RecordInterpreter.interpret(record)
        assertEquals("https://example.com/x", (parsed as ParsedRecord.Uri).uri)
    }

    @Test
    fun smartPosterAndOtherWellKnownTypesAreUnknownInPhaseOne() {
        val smartPoster = NdefRecord(Tnf.WELL_KNOWN, utf8("Sp"), payload = "D1 01 02 55 03".hex())
        assertEquals(ParsedRecord.Unknown(smartPoster), RecordInterpreter.interpret(smartPoster))
    }

    @Test
    fun emptyAndUnknownTnfRecordsAreUnknown() {
        val empty = NdefRecord(Tnf.EMPTY, Bytes.EMPTY, payload = Bytes.EMPTY)
        val unknown = NdefRecord(Tnf.UNKNOWN, Bytes.EMPTY, payload = "01 02".hex())
        assertEquals(ParsedRecord.Unknown(empty), RecordInterpreter.interpret(empty))
        assertEquals(ParsedRecord.Unknown(unknown), RecordInterpreter.interpret(unknown))
    }

    @Test
    fun emptyUriPayloadFallsBackToUnknown() {
        val record = NdefRecord(Tnf.WELL_KNOWN, NdefRecord.TYPE_URI, payload = Bytes.EMPTY)
        assertEquals(ParsedRecord.Unknown(record), RecordInterpreter.interpret(record))
    }

    @Test
    fun interpretingAMessageKeepsRecordOrder() {
        val message = NdefMessage(
            listOf(
                NdefRecord.text("hi"),
                NdefRecord.uri("https://example.com"),
                NdefRecord(Tnf.EXTERNAL, utf8("android.com:pkg"), payload = utf8("com.a")),
            ),
        )
        val parsed = RecordInterpreter.interpret(message)
        assertEquals(listOf("Text", "Uri", "External"), parsed.map { it::class.simpleName })
    }
}
