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

    // ---- real-world tags: codes outside the three this app writes must still decode (W2) -------------------

    @Test
    fun wpaWpa2MixedWithTkipAesStillDecodesAsAWifiCredential() {
        val parsed = WifiWsc.parse(RawWsc.payload("Home", "pass1234", RawWsc.AUTH_WPA_WPA2_MIXED, RawWsc.ENC_TKIP_AES))
        assertEquals("Home", parsed?.ssid)
        assertEquals("pass1234", parsed?.key)
        assertEquals(RawWsc.AUTH_WPA_WPA2_MIXED, parsed?.authCode)
        assertEquals(RawWsc.ENC_TKIP_AES, parsed?.encryptionCode)
    }

    @Test
    fun wpa3AndCompletelyUnknownCodesAreKeptRaw() {
        val wpa3 = WifiWsc.parse(RawWsc.payload("Lab", "sae-secret-9", RawWsc.AUTH_WPA3_SAE, 0x0008))
        assertEquals(RawWsc.AUTH_WPA3_SAE, wpa3?.authCode)
        assertEquals(WifiEncryption.AES, wpa3?.encryption)
        assertNull(wpa3?.auth, "WPA3 is not a WifiAuth the form can write")

        val odd = WifiWsc.parse(RawWsc.payload("Odd", "k3y-value", 0x1234, 0x00F0))
        assertEquals(0x1234, odd?.authCode)
        assertEquals(0x00F0, odd?.encryptionCode)
        assertNull(odd?.auth)
        assertNull(odd?.encryption)
    }

    @Test
    fun knownCodesStillExposeTheirEnums() {
        val parsed = WifiWsc.parse(wpa2Payload)
        assertEquals(WifiAuth.WPA2_PSK, parsed?.auth)
        assertEquals(WifiEncryption.AES, parsed?.encryption)
    }

    // ---- password state (W1) ------------------------------------------------------------------------------

    @Test
    fun passwordStateDistinguishesOpenNotStoredAndPresent() {
        assertEquals(WifiPasswordState.None, WifiCredentials("Cafe", null, WifiAuth.OPEN, WifiEncryption.NONE).passwordState)
        assertEquals(WifiPasswordState.Present("pass1234"), home.passwordState)
        assertEquals(WifiPasswordState.NotStored, home.copy(key = null).passwordState)
        assertEquals(WifiPasswordState.NotStored, home.copy(key = "").passwordState)
        // a code this app does not model is still a secured network
        val mixed = WifiWsc.parse(RawWsc.payload("Home", "", RawWsc.AUTH_WPA_WPA2_MIXED, RawWsc.ENC_TKIP_AES))!!
        assertEquals(WifiPasswordState.NotStored, mixed.passwordState)
    }

    // ---- network key redaction at TLV level ----------------------------------------------------------------

    @Test
    fun redactionEmptiesTheKeyButKeepsEveryOtherAttribute() {
        val redacted = WifiWsc.redactNetworkKey(wpa2Payload)
        assertEquals(WifiWsc.encode(home.copy(key = null)), redacted)
        assertEquals("Home", WifiWsc.parse(redacted)?.ssid)
        assertNull(WifiWsc.parse(redacted)?.key)
    }

    @Test
    fun redactionKeepsUnknownCodesAndAttributesIntact() {
        val original = RawWsc.payload("Home", "pass1234", RawWsc.AUTH_WPA_WPA2_MIXED, RawWsc.ENC_TKIP_AES)
        val redacted = WifiWsc.redactNetworkKey(original)
        assertEquals(original.size - "pass1234".length, redacted.size)
        assertEquals(false, RawWsc.hexOf("pass1234") in redacted.toHex())
        assertEquals(RawWsc.AUTH_WPA_WPA2_MIXED, WifiWsc.parse(redacted)?.authCode)
    }

    @Test
    fun anUnwalkablePayloadIsDroppedAndAnEmptyOneStaysEmpty() {
        assertEquals(Bytes.EMPTY, WifiWsc.redactNetworkKey("10 4A 00".hex()))
        assertEquals(Bytes.EMPTY, WifiWsc.redactNetworkKey(Bytes.EMPTY))
    }

    @Test
    fun garbageParsesToNull() {
        assertNull(WifiWsc.parse("10 4A 00".hex()))
        assertNull(WifiWsc.parse(Bytes.EMPTY))
    }
}
