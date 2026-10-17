package com.tagforge.domain.ndef.spec

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** NC-08: MIME vCard 3.0 with CRLF line endings and escaping. */
class VCardTest {

    @Test
    fun minimalCardMatchesSpecPayloadAndRecordHeader() {
        val payload = VCard.build(VCardFields(name = "Ana"))
        assertEquals("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Ana\r\nEND:VCARD", payload)
        assertEquals(43, payload.toByteArray().size)

        val encoded = DefaultNdefCodec.encode(NdefMessage(listOf(NdefRecord.vcard(payload))))
        assertEquals("D2 0A 2B 74 65 78 74 2F 76 63 61 72 64".hex(), encoded.slice(0, 13))
    }

    @Test
    fun fullCardKeepsFieldOrderAndCrlf() {
        val payload = VCard.build(
            VCardFields(name = "Ana Ruiz", phone = "+573001234567", email = "ana@b.co", org = "Forge", url = "https://b.co", note = "hi"),
        )
        assertEquals(
            "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Ana Ruiz\r\nTEL:+573001234567\r\nEMAIL:ana@b.co\r\nORG:Forge\r\nURL:https://b.co\r\nNOTE:hi\r\nEND:VCARD",
            payload,
        )
    }

    @Test
    fun semicolonsCommasBackslashesAndNewlinesAreEscaped() {
        val payload = VCard.build(VCardFields(name = "Ruiz; Ana, Jr\\", note = "a\nb"))
        assertEquals("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Ruiz\\; Ana\\, Jr\\\\\r\nNOTE:a\\nb\r\nEND:VCARD", payload)
    }

    @Test
    fun parseRoundTripsEscapedFields() {
        val fields = VCardFields(name = "Ruiz; Ana, Jr", phone = "+57 300", email = "a@b.co", org = "A;B", url = null, note = "line1\nline2")
        assertEquals(fields, VCard.parse(VCard.build(fields)))
    }

    @Test
    fun parseIgnoresUnknownPropertiesAndParameters() {
        val text = "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Ruiz;Ana;;;\r\nFN:Ana Ruiz\r\nTEL;TYPE=CELL:+57300\r\nX-FOO:bar\r\nEND:VCARD"
        assertEquals(VCardFields(name = "Ana Ruiz", phone = "+57300"), VCard.parse(text))
    }

    @Test
    fun parseReturnsNullWhenNotAVCard() {
        assertNull(VCard.parse("hello"))
        assertNull(VCard.parse("BEGIN:VCARD\r\nVERSION:3.0\r\nEND:VCARD"))   // no FN
    }

    @Test
    fun recordPayloadIsTheCardTextInUtf8() {
        val record = NdefRecord.vcard(VCard.build(VCardFields(name = "Ñandú")))
        assertEquals(Bytes.copyOf(VCard.build(VCardFields(name = "Ñandú")).toByteArray(Charsets.UTF_8)), record.payload)
    }
}
