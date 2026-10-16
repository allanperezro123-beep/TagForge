package com.tagforge.domain.ndef

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.hex
import kotlin.test.Test
import kotlin.test.assertEquals

/** Golden-byte scenarios for NC-01, NC-02 and NC-03 (spec ndef-codec). */
class NdefEncodeTest {

    private val codec: NdefCodec = DefaultNdefCodec

    private fun encode(vararg records: NdefRecord): Bytes = codec.encode(NdefMessage(records.toList()))

    // ---- NC-01 header -------------------------------------------------------------------------

    @Test
    fun textHiEnglishExactBytes() {
        assertEquals("D1 01 05 54 02 65 6E 68 69".hex(), encode(NdefRecord.text("hi", "en")))
    }

    @Test
    fun spanishTextExactBytes() {
        assertEquals("D1 01 07 54 02 65 73 68 6F 6C 61".hex(), encode(NdefRecord.text("hola", "es")))
    }

    @Test
    fun shortRecordBoundaryAt255Bytes() {
        // payload = 1 status + 2 lang + 252 text = 255 -> still a short record
        val bytes = encode(NdefRecord.text("a".repeat(252), "en"))
        assertEquals("D1 01 FF 54".hex(), bytes.slice(0, 4))
        assertEquals(259, bytes.size)
    }

    @Test
    fun longRecordAt256BytesUsesFourByteLength() {
        val bytes = encode(NdefRecord.text("a".repeat(253), "en"))
        assertEquals("C1 01 00 00 01 00 54 02 65 6E 61 61".hex(), bytes.slice(0, 12))
        assertEquals(263, bytes.size)
    }

    @Test
    fun idFieldSetsIlAndFollowsTypeLength() {
        val record = NdefRecord(Tnf.WELL_KNOWN, NdefRecord.TYPE_TEXT, id = "31".hex(), payload = "02 65 6E 68 69".hex())
        // flags D9 = MB|ME|SR|IL|TNF1 ; type len, payload len, id len, type, id, payload
        assertEquals("D9 01 05 01 54 31 02 65 6E 68 69".hex(), encode(record))
    }

    @Test
    fun emptyRecordFormIsDzeroZeroZero() {
        val empty = NdefRecord(Tnf.EMPTY, Bytes.EMPTY, payload = Bytes.EMPTY)
        assertEquals("D0 00 00".hex(), encode(empty))
    }

    // ---- NC-02 multi-record ---------------------------------------------------------------------

    @Test
    fun twoRecordsSetMbOnFirstAndMeOnLast() {
        val bytes = encode(NdefRecord.text("hi", "en"), NdefRecord.uri("https://example.com"))
        assertEquals(
            "91 01 05 54 02 65 6E 68 69 51 01 0C 55 04 65 78 61 6D 70 6C 65 2E 63 6F 6D".hex(),
            bytes,
        )
    }

    @Test
    fun threeRecordsHaveMiddleWithoutMbOrMe() {
        val aar = NdefRecord(
            Tnf.EXTERNAL,
            Bytes.copyOf("android.com:pkg".toByteArray()),
            payload = Bytes.copyOf("com.a".toByteArray()),
        )
        val bytes = encode(NdefRecord.text("hi", "en"), NdefRecord.uri("https://example.com"), aar)

        val first = 0
        val second = 9
        val third = 9 + 16
        assertEquals(0x91, bytes[first])
        assertEquals(0x11, bytes[second])
        assertEquals(0x54, bytes[third])
        assertEquals(
            "54 0F 05 61 6E 64 72 6F 69 64 2E 63 6F 6D 3A 70 6B 67 63 6F 6D 2E 61".hex(),
            bytes.slice(third),
        )
    }

    @Test
    fun emptyMessageEncodesToZeroBytes() {
        assertEquals(Bytes.EMPTY, codec.encode(NdefMessage.EMPTY))
    }

    // ---- NC-03 Text / URI -----------------------------------------------------------------------

    @Test
    fun uriHttpsExactBytes() {
        assertEquals("D1 01 0C 55 04 65 78 61 6D 70 6C 65 2E 63 6F 6D".hex(), encode(NdefRecord.uri("https://example.com")))
    }

    @Test
    fun uriLongestPrefixWinsInEncodedBytes() {
        assertEquals(0x02, encode(NdefRecord.uri("https://www.example.com"))[4])
    }

    @Test
    fun unprefixedGeoExactBytes() {
        assertEquals(
            "D1 01 13 55 00 67 65 6F 3A 34 2E 37 31 31 2C 2D 37 34 2E 30 37 32 31".hex(),
            encode(NdefRecord.uri("geo:4.711,-74.0721")),
        )
    }

    @Test
    fun telAndMailtoExactBytes() {
        assertEquals("D1 01 0E 55 05 2B 35 37 33 30 30 31 32 33 34 35 36 37".hex(), encode(NdefRecord.uri("tel:+573001234567")))
        assertEquals("D1 01 07 55 06 61 40 62 2E 63 6F".hex(), encode(NdefRecord.uri("mailto:a@b.co")))
    }
}
