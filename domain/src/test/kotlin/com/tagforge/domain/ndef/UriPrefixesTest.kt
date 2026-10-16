package com.tagforge.domain.ndef

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UriPrefixesTest {

    @Test
    fun tableMatchesNfcForumAbbreviations() {
        val expected = mapOf(
            0x00 to "", 0x01 to "http://www.", 0x02 to "https://www.", 0x03 to "http://", 0x04 to "https://",
            0x05 to "tel:", 0x06 to "mailto:", 0x07 to "ftp://anonymous:anonymous@", 0x08 to "ftp://ftp.",
            0x09 to "ftps://", 0x0A to "sftp://", 0x0B to "smb://", 0x0C to "nfs://", 0x0D to "ftp://",
            0x0E to "dav://", 0x0F to "news:", 0x10 to "telnet://", 0x11 to "imap:", 0x12 to "rtsp://",
            0x13 to "urn:", 0x14 to "pop:", 0x15 to "sip:", 0x16 to "sips:", 0x17 to "tftp:",
            0x18 to "btspp://", 0x19 to "btl2cap://", 0x1A to "btgoep://", 0x1B to "tcpobex://",
            0x1C to "irdaobex://", 0x1D to "file://", 0x1E to "urn:epc:id:", 0x1F to "urn:epc:tag:",
            0x20 to "urn:epc:pat:", 0x21 to "urn:epc:raw:", 0x22 to "urn:epc:", 0x23 to "urn:nfc:",
        )
        assertEquals(0x24, expected.size)
        for ((code, prefix) in expected) assertEquals(prefix, UriPrefixes.prefixFor(code), "code 0x%02X".format(code))
    }

    @Test
    fun codesAboveTableAreUnknown() {
        assertNull(UriPrefixes.prefixFor(0x24))
        assertNull(UriPrefixes.prefixFor(0x7F))
    }

    @Test
    fun longestPrefixWins() {
        assertEquals(UriPrefixes.Split(0x02, "example.com"), UriPrefixes.split("https://www.example.com"))
        assertEquals(UriPrefixes.Split(0x04, "example.com"), UriPrefixes.split("https://example.com"))
        assertEquals(UriPrefixes.Split(0x01, "a.org"), UriPrefixes.split("http://www.a.org"))
        assertEquals(UriPrefixes.Split(0x03, "a.org"), UriPrefixes.split("http://a.org"))
    }

    @Test
    fun longestPrefixWinsAmongUrnVariants() {
        assertEquals(UriPrefixes.Split(0x23, "x"), UriPrefixes.split("urn:nfc:x"))
        assertEquals(UriPrefixes.Split(0x1E, "x"), UriPrefixes.split("urn:epc:id:x"))
        assertEquals(UriPrefixes.Split(0x22, "other"), UriPrefixes.split("urn:epc:other"))
        assertEquals(UriPrefixes.Split(0x13, "isbn:1"), UriPrefixes.split("urn:isbn:1"))
    }

    @Test
    fun unprefixedSchemesUseCodeZeroAndKeepTheWholeString() {
        assertEquals(UriPrefixes.Split(0x00, "geo:4.711,-74.0721"), UriPrefixes.split("geo:4.711,-74.0721"))
        assertEquals(UriPrefixes.Split(0x00, "sms:123?body=hi"), UriPrefixes.split("sms:123?body=hi"))
        assertEquals(UriPrefixes.Split(0x00, ""), UriPrefixes.split(""))
    }

    @Test
    fun telAndMailtoUseTheirOwnCodes() {
        assertEquals(UriPrefixes.Split(0x05, "+573001234567"), UriPrefixes.split("tel:+573001234567"))
        assertEquals(UriPrefixes.Split(0x06, "a@b.co"), UriPrefixes.split("mailto:a@b.co"))
    }

    @Test
    fun joinIsTheInverseOfSplit() {
        for (uri in listOf("https://www.example.com/x", "geo:1,2", "tel:+57", "urn:nfc:ext:a:b")) {
            val split = UriPrefixes.split(uri)
            assertEquals(uri, UriPrefixes.join(split.code, split.rest))
        }
        assertNull(UriPrefixes.join(0x7F, "whatever"))
    }
}
