package com.tagforge.domain.ndef.spec

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.Tnf

/**
 * Hand-built WSC payloads that deliberately bypass [WifiWsc.encode], so tests can carry auth/encryption codes the
 * app never writes itself (WPA/WPA2 mixed 0x0022, WPA3-SAE 0x0040, TKIP+AES 0x000C, ...) and malformed layouts.
 */
internal object RawWsc {

    const val AUTH_WPA_WPA2_MIXED = 0x0022
    const val AUTH_WPA3_SAE = 0x0040
    const val ENC_TKIP_AES = 0x000C

    fun attr(id: Int, value: Bytes): Bytes =
        Bytes.of(id ushr 8, id and 0xFF, value.size ushr 8, value.size and 0xFF) + value

    fun text(id: Int, value: String): Bytes = attr(id, Bytes.copyOf(value.toByteArray(Charsets.UTF_8)))

    fun u16(id: Int, value: Int): Bytes = attr(id, Bytes.of(value ushr 8, value and 0xFF))

    /** A complete, well-formed WSC payload (Version + Credential) with arbitrary codes. */
    fun payload(ssid: String, key: String, auth: Int, encryption: Int): Bytes {
        val credential = Bytes.concat(
            listOf(
                attr(0x1026, Bytes.of(1)),
                text(0x1045, ssid),
                u16(0x1003, auth),
                u16(0x100F, encryption),
                text(0x1027, key),
                attr(0x1020, Bytes.of(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF)),
            ),
        )
        return attr(0x104A, Bytes.of(0x10)) + attr(0x100E, credential)
    }

    fun record(payload: Bytes, mimeType: String = WifiWsc.MIME_TYPE): NdefRecord =
        NdefRecord(Tnf.MIME, Bytes.copyOf(mimeType.toByteArray(Charsets.US_ASCII)), payload = payload)

    fun hexOf(text: String): String = Bytes.copyOf(text.toByteArray(Charsets.UTF_8)).toHex()
}
