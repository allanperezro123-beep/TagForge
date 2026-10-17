package com.tagforge.domain.ndef.spec

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.ParsedRecord
import com.tagforge.domain.ndef.RecordInterpreter
import com.tagforge.domain.ndef.Tnf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** NC-11: URI builders (mailto, sms, geo, tel) and Smart Poster parsing. */
class UriBuildersTest {

    @Test
    fun mailtoPercentEncodesSubjectWithPrefixSix() {
        val uri = UriBuilders.mailto("a@b.co", subject = "Hi there")
        assertEquals("mailto:a@b.co?subject=Hi%20there", uri)
        val record = NdefRecord.uri(uri)
        assertEquals("06".hex(), record.payload.slice(0, 1))
        assertEquals(Bytes.copyOf("a@b.co?subject=Hi%20there".toByteArray()), record.payload.slice(1))
    }

    @Test
    fun mailtoWithSubjectAndBodyEncodesBothAndReservedCharacters() {
        assertEquals(
            "mailto:a@b.co?subject=A%26B&body=x%3Dy%0Aline2",
            UriBuilders.mailto("a@b.co", subject = "A&B", body = "x=y\nline2"),
        )
    }

    @Test
    fun mailtoWithoutSubjectOrBodyIsPlain() {
        assertEquals("mailto:a@b.co", UriBuilders.mailto("a@b.co"))
        assertEquals("mailto:a@b.co?body=hey", UriBuilders.mailto("a@b.co", body = "hey"))
    }

    @Test
    fun mailtoEncodesNonAsciiAsUtf8() {
        assertEquals("mailto:a@b.co?subject=Se%C3%B1or", UriBuilders.mailto("a@b.co", subject = "Señor"))
    }

    @Test
    fun smsAppendsBodyOnlyWhenPresent() {
        assertEquals("sms:+573001234567?body=Hola%20mundo", UriBuilders.sms("+57 300 123 4567", "Hola mundo"))
        assertEquals("sms:+573001234567", UriBuilders.sms("+57 300 123 4567"))
    }

    @Test
    fun telKeepsPlusAndDigitsOnly() {
        assertEquals("tel:+573001234567", UriBuilders.tel("+57 (300) 123-4567"))
        assertEquals("tel:3001234567", UriBuilders.tel("300 123 4567"))
    }

    @Test
    fun geoFormatsCoordinatesAndOptionalQuery() {
        assertEquals("geo:4.711,-74.0721", UriBuilders.geo(4.711, -74.0721))
        assertEquals("geo:4.711,-74.0721?q=Caf%C3%A9%20Juan", UriBuilders.geo(4.711, -74.0721, query = "Café Juan"))
        assertEquals("geo:0.00001,10", UriBuilders.geo(0.00001, 10.0))   // never scientific notation
    }

    @Test
    fun smartPosterWithTitleAndUriIsInterpreted() {
        val inner = NdefMessage(listOf(NdefRecord.uri("https://example.com"), NdefRecord.text("Example", "en")))
        val poster = SmartPoster.record(inner)
        assertEquals(Tnf.WELL_KNOWN, poster.tnf)
        assertEquals(Bytes.of(0x53, 0x70), poster.type)

        val parsed = RecordInterpreter.interpret(poster)
        assertIs<ParsedRecord.SmartPoster>(parsed)
        assertEquals("https://example.com", parsed.uri)
        assertEquals("Example", parsed.title)
    }

    @Test
    fun smartPosterWithoutTitleHasNullTitleAndWithoutUriIsUnknown() {
        val noTitle = SmartPoster.record(NdefMessage(listOf(NdefRecord.uri("tel:+57300"))))
        assertEquals(null, (RecordInterpreter.interpret(noTitle) as ParsedRecord.SmartPoster).title)

        val noUri = SmartPoster.record(NdefMessage(listOf(NdefRecord.text("only title", "en"))))
        assertIs<ParsedRecord.Unknown>(RecordInterpreter.interpret(noUri))
    }
}
