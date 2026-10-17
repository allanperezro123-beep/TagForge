package com.tagforge.domain.ndef

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.spec.BluetoothAddress
import com.tagforge.domain.ndef.spec.BluetoothOob
import com.tagforge.domain.ndef.spec.RawWsc
import com.tagforge.domain.ndef.spec.VCard
import com.tagforge.domain.ndef.spec.VCardFields
import com.tagforge.domain.ndef.spec.WifiAuth
import com.tagforge.domain.ndef.spec.WifiCredentials
import com.tagforge.domain.ndef.spec.WifiEncryption
import com.tagforge.domain.ndef.spec.WifiWsc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** TS-04: Text, URI, generic MIME, External, Unknown plus the Phase 2 rich decoders (WiFi, Bluetooth, vCard, AAR, Smart Poster). */
class RecordInterpreterTest {

    private val CRLF = String(charArrayOf(Char(13), Char(10)))

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
        val record = NdefRecord(Tnf.EXTERNAL, utf8("example.com:ticket"), payload = utf8("42"))
        val parsed = RecordInterpreter.interpret(record)
        assertIs<ParsedRecord.External>(parsed)
        assertEquals("example.com", parsed.domain)
        assertEquals("ticket", parsed.type)
        assertEquals(utf8("42"), parsed.payload)
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
    fun truncatedSmartPosterBodyStaysUnknown() {
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
                NdefRecord.aar("com.a"),
            ),
        )
        val parsed = RecordInterpreter.interpret(message)
        assertEquals(listOf("Text", "Uri", "Aar"), parsed.map { it::class.simpleName })
    }

    // ---- Phase 2 rich decoders (TS-04 mixed message, reconciliation #9) ----------------------------

    @Test
    fun interpretsWifiCredentialRecord() {
        val creds = WifiCredentials("Home", "pass1234", WifiAuth.WPA2_PSK, WifiEncryption.AES)
        val parsed = RecordInterpreter.interpret(WifiWsc.record(creds))
        assertIs<ParsedRecord.Wifi>(parsed)
        assertEquals(creds, parsed.credentials)
    }

    @Test
    fun wifiRecordsWithCodesTheAppDoesNotWriteStillRenderAsWifi() {
        val mixed = RawWsc.record(RawWsc.payload("Home", "pass1234", RawWsc.AUTH_WPA_WPA2_MIXED, RawWsc.ENC_TKIP_AES))
        val parsedMixed = RecordInterpreter.interpret(mixed)
        assertIs<ParsedRecord.Wifi>(parsedMixed)
        assertEquals("Home", parsedMixed.credentials.ssid)

        val wpa3 = RawWsc.record(RawWsc.payload("Lab", "sae-secret-9", RawWsc.AUTH_WPA3_SAE, 0x0008))
        assertEquals("Lab", (RecordInterpreter.interpret(wpa3) as ParsedRecord.Wifi).credentials.ssid)
    }

    @Test
    fun unparsableWifiPayloadFallsBackToGenericMime() {
        val record = NdefRecord.mime(WifiWsc.MIME_TYPE, "10 4A 00".hex())
        assertIs<ParsedRecord.Mime>(RecordInterpreter.interpret(record))
    }

    @Test
    fun interpretsBluetoothOobRecord() {
        val parsed = RecordInterpreter.interpret(BluetoothOob.record("AA:BB:CC:DD:EE:FF", "X"))
        assertIs<ParsedRecord.Bluetooth>(parsed)
        assertEquals(BluetoothAddress("AA:BB:CC:DD:EE:FF", "X"), parsed.address)
    }

    @Test
    fun interpretsVCardRecord() {
        val fields = VCardFields(name = "Ana", phone = "+57300")
        val parsed = RecordInterpreter.interpret(NdefRecord.vcard(VCard.build(fields)))
        assertIs<ParsedRecord.VCard>(parsed)
        assertEquals(fields, parsed.fields)
    }

    @Test
    fun vCardWithoutNameStaysGenericMime() {
        val record = NdefRecord.vcard("BEGIN:VCARD" + CRLF + "VERSION:3.0" + CRLF + "END:VCARD")
        assertIs<ParsedRecord.Mime>(RecordInterpreter.interpret(record))
    }

    @Test
    fun interpretsAndroidApplicationRecordWithItsPackage() {
        val parsed = RecordInterpreter.interpret(NdefRecord.aar("com.tagforge.app"))
        assertIs<ParsedRecord.Aar>(parsed)
        assertEquals("com.tagforge.app", parsed.packageName)
    }
}
