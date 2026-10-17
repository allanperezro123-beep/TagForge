package com.tagforge.domain.ndef.spec

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** TW-01 record types and HT-02 "prefilled with identical records": spec -> record, and spec <-> JSON. */
class RecordSpecEncoderTest {

    private fun encode(spec: RecordSpec): NdefRecord? = RecordSpecEncoder.encode(spec)

    @Test
    fun textAndUriMapToTheirRtdBuilders() {
        assertEquals(NdefRecord.text("hola", "es"), encode(RecordSpec.Text("hola", "es")))
        assertEquals(NdefRecord.uri("https://example.com"), encode(RecordSpec.Uri("https://example.com")))
    }

    @Test
    fun uriIsTrimmed() {
        assertEquals(NdefRecord.uri("https://example.com"), encode(RecordSpec.Uri("  https://example.com ")))
    }

    @Test
    fun emailBecomesMailtoWithEncodedSubject() {
        val record = encode(RecordSpec.Email(to = "a@b.co", subject = "Hi there"))
        assertEquals(NdefRecord.uri("mailto:a@b.co?subject=Hi%20there"), record)
    }

    @Test
    fun phoneBecomesTelAndStripsFormatting() {
        assertEquals(NdefRecord.uri("tel:+573001234567"), encode(RecordSpec.Phone("+57 (300) 123-4567")))
    }

    @Test
    fun smsBecomesSmsUriWithBody() {
        assertEquals(NdefRecord.uri("sms:+573001234567?body=Hola"), encode(RecordSpec.Sms("+57 300 123 4567", "Hola")))
    }

    @Test
    fun geoBecomesGeoUriFromCoordinateText() {
        assertEquals(NdefRecord.uri("geo:4.711,-74.0721"), encode(RecordSpec.Geo("4.711", "-74.0721")))
        assertEquals(NdefRecord.uri("geo:4.711,-74.0721?q=Casa"), encode(RecordSpec.Geo("4.711", " -74.0721 ", "Casa")))
    }

    @Test
    fun contactBecomesVCardMimeRecord() {
        val record = encode(RecordSpec.Contact(name = "Ana", phone = "+57300"))
        assertEquals(NdefRecord.vcard(VCard.build(VCardFields(name = "Ana", phone = "+57300"))), record)
    }

    @Test
    fun wifiBecomesWscRecordAndOpenNetworksDropTheKey() {
        val wpa = encode(RecordSpec.Wifi("Home", "pass1234", WifiAuth.WPA2_PSK, WifiEncryption.AES))
        assertEquals(WifiWsc.record(WifiCredentials("Home", "pass1234", WifiAuth.WPA2_PSK, WifiEncryption.AES)), wpa)

        val open = encode(RecordSpec.Wifi("Cafe", "ignored", WifiAuth.OPEN, WifiEncryption.AES))
        assertEquals(WifiWsc.record(WifiCredentials("Cafe", null, WifiAuth.OPEN, WifiEncryption.NONE)), open)
    }

    @Test
    fun bluetoothAppAndMimeAndExternalRecords() {
        assertEquals(BluetoothOob.record("AA:BB:CC:DD:EE:FF", "X"), encode(RecordSpec.Bluetooth("AA:BB:CC:DD:EE:FF", "X")))
        assertEquals(BluetoothOob.record("AA:BB:CC:DD:EE:FF", null), encode(RecordSpec.Bluetooth("AA:BB:CC:DD:EE:FF")))
        assertEquals(NdefRecord.aar("com.a"), encode(RecordSpec.AppLaunch("com.a")))
        assertEquals(
            NdefRecord.mime("text/plain", Bytes.copyOf("hello".toByteArray())),
            encode(RecordSpec.Mime("text/plain", "hello")),
        )
        assertEquals(
            NdefRecord.external("example.com", "t", "01 02".hex()),
            encode(RecordSpec.External("example.com", "t", "01 02")),
        )
    }

    @Test
    fun invalidSpecEncodesToNullInsteadOfThrowing() {
        assertNull(encode(RecordSpec.Text("")))
        assertNull(encode(RecordSpec.Geo("200", "0")))
        assertNull(encode(RecordSpec.Wifi("Home", "123", WifiAuth.WPA2_PSK, WifiEncryption.AES)))
    }

    @Test
    fun messageEncodingKeepsOrderAndAppendsAarLast() {
        val specs = listOf(RecordSpec.Text("hi", "en"), RecordSpec.Uri("https://example.com"))
        val message = RecordSpecEncoder.encodeMessage(specs, aarPackage = "com.tagforge.app")
        assertEquals(
            listOf(NdefRecord.text("hi", "en"), NdefRecord.uri("https://example.com"), NdefRecord.aar("com.tagforge.app")),
            message?.records,
        )
    }

    @Test
    fun messageWithAnyInvalidRecordIsNull() {
        assertNull(RecordSpecEncoder.encodeMessage(listOf(RecordSpec.Text("hi"), RecordSpec.Uri("")), aarPackage = null))
    }

    @Test
    fun threeRecordMessageMatchesSpecBytes() {
        val message = RecordSpecEncoder.encodeMessage(
            listOf(RecordSpec.Text("hi", "en"), RecordSpec.Uri("https://example.com")),
            aarPackage = "com.a",
        ) as NdefMessage
        val bytes = DefaultNdefCodec.encode(message)
        assertEquals("91".hex(), bytes.slice(0, 1))
        assertEquals("54 0F 05".hex(), bytes.slice(bytes.size - 23, bytes.size - 20))
    }

    // ---- templates: JSON round trip ------------------------------------------------------------

    private val everyKind = listOf(
        RecordSpec.Text("hola", "es"),
        RecordSpec.Uri("https://example.com"),
        RecordSpec.Email("a@b.co", "Hi", "Body"),
        RecordSpec.Phone("+57300"),
        RecordSpec.Sms("+57300", "Hola"),
        RecordSpec.Geo("4.7", "-74.0", "Casa"),
        RecordSpec.Contact("Ana", "+57300", "a@b.co", "Forge", "https://b.co", "nota"),
        RecordSpec.Wifi("Home", "pass1234", WifiAuth.WPA_PSK, WifiEncryption.TKIP),
        RecordSpec.Bluetooth("AA:BB:CC:DD:EE:FF", "X"),
        RecordSpec.AppLaunch("com.a"),
        RecordSpec.Mime("text/plain", "hello"),
        RecordSpec.External("example.com", "t", "0102"),
    )

    @Test
    fun jsonRoundTripPreservesEveryRecordKind() {
        val json = RecordSpecJson.encode(everyKind)
        assertEquals(everyKind, RecordSpecJson.decode(json))
    }

    @Test
    fun jsonUsesStableTypeCodes() {
        val json = RecordSpecJson.encode(listOf(RecordSpec.Text("hi"), RecordSpec.AppLaunch("com.a")))
        assertEquals(true, "\"text\"" in json && "\"app\"" in json, json)
    }

    @Test
    fun decodedTemplateEncodesToIdenticalRecords() {
        val before = RecordSpecEncoder.encodeMessage(everyKind, null)
        val after = RecordSpecEncoder.encodeMessage(RecordSpecJson.decode(RecordSpecJson.encode(everyKind))!!, null)
        assertEquals(before, after)
    }

    @Test
    fun brokenOrUnknownJsonDecodesToNull() {
        assertNull(RecordSpecJson.decode("not json"))
        assertNull(RecordSpecJson.decode("[{\"t\":\"future-kind\"}]"))
    }

    @Test
    fun unknownFieldsFromNewerVersionsAreIgnored() {
        val decoded = RecordSpecJson.decode("[{\"t\":\"text\",\"text\":\"hi\",\"languageCode\":\"en\",\"extra\":1}]")
        assertEquals(listOf(RecordSpec.Text("hi", "en")), decoded)
    }
}
