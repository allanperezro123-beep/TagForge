package com.tagforge.domain.exchange

import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.WifiAuth
import com.tagforge.domain.ndef.spec.WifiEncryption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** PR-02: what a QR code says maps to a record the wizard can edit, and back for the kinds a QR can carry. */
class QrPayloadMapperTest {

    // ---- decode ----------------------------------------------------------------------------------------------------

    @Test
    fun `wifi qr becomes an editable wifi record`() {
        assertEquals(
            RecordSpec.Wifi("Home", "pass1234", WifiAuth.WPA2_PSK, WifiEncryption.AES),
            QrPayloadMapper.fromQr("WIFI:T:WPA;S:Home;P:pass1234;;"),
        )
    }

    @Test
    fun `open wifi qr has no password and no encryption`() {
        assertEquals(
            RecordSpec.Wifi("Cafe", "", WifiAuth.OPEN, WifiEncryption.NONE),
            QrPayloadMapper.fromQr("WIFI:T:nopass;S:Cafe;;"),
        )
    }

    @Test
    fun `wifi qr unescapes semicolon colon backslash and quote`() {
        val decoded = QrPayloadMapper.fromQr("""WIFI:T:WPA;S:a\;b\:c;P:p\\w\"d;;""")
        assertEquals(RecordSpec.Wifi("a;b:c", """p\w"d""", WifiAuth.WPA2_PSK, WifiEncryption.AES), decoded)
    }

    @Test
    fun `wifi qr fields may come in any order`() {
        assertEquals(
            RecordSpec.Wifi("Home", "pw123456", WifiAuth.WPA2_PSK, WifiEncryption.AES),
            QrPayloadMapper.fromQr("WIFI:S:Home;P:pw123456;T:WPA;;"),
        )
    }

    @Test
    fun `wifi qr without a network name is kept as text`() {
        assertEquals(RecordSpec.Text("WIFI:T:WPA;P:x;;"), QrPayloadMapper.fromQr("WIFI:T:WPA;P:x;;"))
    }

    @Test
    fun `urls become uri records`() {
        assertEquals(RecordSpec.Uri("https://example.com/a?b=1"), QrPayloadMapper.fromQr("https://example.com/a?b=1"))
        assertEquals(RecordSpec.Uri("http://example.com"), QrPayloadMapper.fromQr("  http://example.com  "))
    }

    @Test
    fun `vcard qr becomes a contact`() {
        val vcard = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Ada Lovelace\r\nTEL:+34600111222\r\nEMAIL:ada@example.com\r\nEND:VCARD"
        assertEquals(
            RecordSpec.Contact(name = "Ada Lovelace", phone = "+34600111222", email = "ada@example.com"),
            QrPayloadMapper.fromQr(vcard),
        )
    }

    @Test
    fun `tel mailto sms and geo map to their record kinds`() {
        assertEquals(RecordSpec.Phone("+34600111222"), QrPayloadMapper.fromQr("tel:+34600111222"))
        assertEquals(
            RecordSpec.Email("a@b.com", "Hi there", "Body text"),
            QrPayloadMapper.fromQr("mailto:a@b.com?subject=Hi%20there&body=Body%20text"),
        )
        assertEquals(RecordSpec.Sms("+34600111222", "ok"), QrPayloadMapper.fromQr("sms:+34600111222?body=ok"))
        assertEquals(RecordSpec.Sms("+34600111222", "hello"), QrPayloadMapper.fromQr("SMSTO:+34600111222:hello"))
        assertEquals(RecordSpec.Geo("40.4168", "-3.7038", ""), QrPayloadMapper.fromQr("geo:40.4168,-3.7038"))
        assertEquals(RecordSpec.Geo("40.4", "-3.7", "Prado"), QrPayloadMapper.fromQr("geo:40.4,-3.7?q=Prado"))
    }

    @Test
    fun `anything else is text and a malformed geo is not lost`() {
        assertEquals(RecordSpec.Text("hello world"), QrPayloadMapper.fromQr("hello world"))
        assertEquals(RecordSpec.Text("geo:north,west"), QrPayloadMapper.fromQr("geo:north,west"))
        assertEquals(RecordSpec.Text("WIFI:T:WEP;S:Old;P:12345;;"), QrPayloadMapper.fromQr("WIFI:T:WEP;S:Old;P:12345;;"))
    }

    // ---- encode ----------------------------------------------------------------------------------------------------

    @Test
    fun `wifi record is written with escaping`() {
        assertEquals(
            "WIFI:T:WPA;S:Home;P:pass1234;;",
            QrPayloadMapper.toQr(RecordSpec.Wifi("Home", "pass1234", WifiAuth.WPA2_PSK, WifiEncryption.AES)),
        )
        assertEquals(
            """WIFI:T:WPA;S:a\;b\:c;P:p\\w\"d;;""",
            QrPayloadMapper.toQr(RecordSpec.Wifi("a;b:c", """p\w"d""", WifiAuth.WPA2_PSK, WifiEncryption.AES)),
        )
        assertEquals("WIFI:T:nopass;S:Cafe;;", QrPayloadMapper.toQr(RecordSpec.Wifi("Cafe", "", WifiAuth.OPEN, WifiEncryption.NONE)))
    }

    @Test
    fun `representable records survive a qr round trip`() {
        val samples = listOf(
            RecordSpec.Text("plain text"),
            RecordSpec.Uri("https://example.com/x"),
            RecordSpec.Phone("+34600111222"),
            RecordSpec.Email("a@b.com", "Hi there", "Body text"),
            RecordSpec.Sms("+34600111222", "ok now"),
            RecordSpec.Geo("40.4168", "-3.7038", "Prado"),
            RecordSpec.Contact(name = "Ada", phone = "+34600111222", org = "Analytical"),
            RecordSpec.Wifi("a;b:c", """p\w"d""", WifiAuth.WPA2_PSK, WifiEncryption.AES),
        )
        for (sample in samples) {
            assertEquals(sample, QrPayloadMapper.fromQr(QrPayloadMapper.toQr(sample)!!), "round trip of $sample")
        }
    }

    @Test
    fun `records a qr cannot carry have no payload`() {
        assertNull(QrPayloadMapper.toQr(RecordSpec.Bluetooth("AA:BB:CC:DD:EE:FF", "x")))
        assertNull(QrPayloadMapper.toQr(RecordSpec.AppLaunch("com.example")))
        assertNull(QrPayloadMapper.toQr(RecordSpec.Mime("text/x-a", "b")))
        assertNull(QrPayloadMapper.toQr(RecordSpec.Geo("north", "west")))
    }

    @Test
    fun `a single record message can be turned into a qr but several cannot`() {
        val one = listOf<RecordSpec>(RecordSpec.Uri("https://example.com"))
        assertEquals("https://example.com", QrPayloadMapper.forMessage(one))
        assertNull(QrPayloadMapper.forMessage(one + RecordSpec.Text("x")))
        assertNull(QrPayloadMapper.forMessage(emptyList()))
    }
}
