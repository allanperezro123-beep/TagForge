package com.tagforge.domain.ndef.spec

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** NC-09: WiFi Simple Configuration credential record. */
class WifiWscTest {

    private val wpa2Payload = (
        "10 4A 00 01 10 10 0E 00 2F 10 26 00 01 01 10 45 00 04 48 6F 6D 65 10 03 00 02 00 20 " +
            "10 0F 00 02 00 08 10 27 00 08 70 61 73 73 31 32 33 34 10 20 00 06 FF FF FF FF FF FF"
        ).hex()

    private val home = WifiCredentials("Home", "pass1234", WifiAuth.WPA2_PSK, WifiEncryption.AES)

    @Test
    fun wpa2PayloadMatchesSpecByteForByte() {
        val payload = WifiWsc.encode(home)
        assertEquals(56, payload.size)
        assertEquals(wpa2Payload, payload)
    }

    @Test
    fun wpa2RecordHeaderIsD21738() {
        val record = WifiWsc.record(home)
        val encoded = DefaultNdefCodec.encode(NdefMessage(listOf(record)))
        assertEquals("D2 17 38".hex(), encoded.slice(0, 3))
        assertEquals(Bytes.copyOf("application/vnd.wfa.wsc".toByteArray()), record.type)
    }

    @Test
    fun openNetworkUsesNoneAuthEncryptionAndEmptyKey() {
        val payload = WifiWsc.encode(WifiCredentials("Cafe", null, WifiAuth.OPEN, WifiEncryption.NONE))
        val hex = payload.toHex(" ")
        assertEquals(true, "10 03 00 02 00 01" in hex, "auth OPEN = 0001")
        assertEquals(true, "10 0F 00 02 00 01" in hex, "encryption NONE = 0001")
        assertEquals(true, "10 27 00 00" in hex, "empty network key attribute")
        assertEquals("10 0E 00 27", payload.slice(5, 9).toHex(" "))   // credential length 0x27
    }

    @Test
    fun parseBackRecoversEveryField() {
        assertEquals(home, WifiWsc.parse(wpa2Payload))
    }

    @Test
    fun parseBackOfOpenNetworkHasNoKey() {
        val open = WifiCredentials("Cafe", null, WifiAuth.OPEN, WifiEncryption.NONE)
        assertEquals(open, WifiWsc.parse(WifiWsc.encode(open)))
    }

    @Test
    fun unknownAttributesAreSkippedWhileParsing() {
        // an unknown top-level attribute (1049 vendor extension) and an unknown attribute inside the credential
        val withExtras = "10 49 00 02 AA BB".hex() + WifiWsc.encode(home).let { original ->
            val credentialStart = 5
            val body = original.slice(credentialStart + 4)
            val extended = body + "10 FF 00 01 07".hex()
            original.slice(0, credentialStart) + Bytes.of(0x10, 0x0E, 0x00, extended.size) + extended
        }
        assertEquals(home, WifiWsc.parse(withExtras))
    }

    @Test
    fun multiByteSsidLengthCountsUtf8Bytes() {
        val creds = WifiCredentials("Café", "pass1234", WifiAuth.WPA2_PSK, WifiEncryption.AES)
        val parsed = WifiWsc.parse(WifiWsc.encode(creds))
        assertEquals("Café", parsed?.ssid)
    }

    @Test
    fun garbageParsesToNull() {
        assertNull(WifiWsc.parse("10 4A 00".hex()))
        assertNull(WifiWsc.parse(Bytes.EMPTY))
    }
}
