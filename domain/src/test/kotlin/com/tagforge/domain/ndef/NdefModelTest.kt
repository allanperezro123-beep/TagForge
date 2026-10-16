package com.tagforge.domain.ndef

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class NdefModelTest {

    @Test
    fun tnfCodesRoundTripAndReservedSevenIsRejected() {
        for (tnf in Tnf.entries) assertEquals(tnf, Tnf.fromCode(tnf.code))
        assertEquals(0, Tnf.EMPTY.code)
        assertEquals(1, Tnf.WELL_KNOWN.code)
        assertEquals(2, Tnf.MIME.code)
        assertEquals(3, Tnf.ABSOLUTE_URI.code)
        assertEquals(4, Tnf.EXTERNAL.code)
        assertEquals(5, Tnf.UNKNOWN.code)
        assertEquals(6, Tnf.UNCHANGED.code)
        assertNull(Tnf.fromCode(7))
    }

    @Test
    fun recordsCompareByContent() {
        val a = NdefRecord(Tnf.MIME, "text/plain".toByteArray().let { Bytes.copyOf(it) }, payload = Bytes.of(1, 2))
        val b = NdefRecord(Tnf.MIME, Bytes.copyOf("text/plain".toByteArray()), payload = Bytes.of(1, 2))
        val c = NdefRecord(Tnf.MIME, Bytes.copyOf("text/plain".toByteArray()), payload = Bytes.of(1, 3))
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertEquals(NdefMessage(listOf(a)), NdefMessage(listOf(b)))
    }

    @Test
    fun textBuilderWritesUtf8WithLanguageStatusByte() {
        val record = NdefRecord.text("hi", "en")
        assertEquals(Tnf.WELL_KNOWN, record.tnf)
        assertEquals("54".hex(), record.type)
        assertEquals("02 65 6E 68 69".hex(), record.payload)

        val spanish = NdefRecord.text("hola", "es")
        assertEquals("02 65 73 68 6F 6C 61".hex(), spanish.payload)
    }

    @Test
    fun textBuilderEncodesNonAsciiAsUtf8() {
        val record = NdefRecord.text("año", "es")  // a, n-tilde (C3 B1), o
        assertEquals("02 65 73 61 C3 B1 6F".hex(), record.payload)
    }

    @Test
    fun uriBuilderUsesTheLongestPrefixCode() {
        val https = NdefRecord.uri("https://example.com")
        assertEquals(Tnf.WELL_KNOWN, https.tnf)
        assertEquals("55".hex(), https.type)
        assertEquals("04 65 78 61 6D 70 6C 65 2E 63 6F 6D".hex(), https.payload)

        assertEquals(0x02, NdefRecord.uri("https://www.example.com").payload[0])
        assertEquals(0x00, NdefRecord.uri("geo:4.711,-74.0721").payload[0])
    }

    @Test
    fun messageExposesRecordsInOrder() {
        // Size is computed by the codec (see NdefEncodeTest).
        val message = NdefMessage(listOf(NdefRecord.text("hi"), NdefRecord.uri("tel:+57")))
        assertEquals(2, message.records.size)
        assertEquals(Tnf.WELL_KNOWN, message.records.last().tnf)
    }
}
