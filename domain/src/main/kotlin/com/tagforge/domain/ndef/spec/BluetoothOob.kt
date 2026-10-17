package com.tagforge.domain.ndef.spec

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ndef.NdefRecord

/** [mac] is uppercase and colon separated, in display order. */
data class BluetoothAddress(val mac: String, val name: String?)

/**
 * Bluetooth Secure Simple Pairing out-of-band record (`application/vnd.bluetooth.ep.oob`).
 * Payload = 2-byte little-endian total length (itself included) + 6-byte MAC in reverse order + EIR structures.
 */
object BluetoothOob {

    const val MIME_TYPE = "application/vnd.bluetooth.ep.oob"

    private const val MAC_BYTES = 6
    private const val LENGTH_FIELD = 2
    private const val EIR_SHORTENED_NAME = 0x08
    private const val EIR_COMPLETE_NAME = 0x09
    private val MAC_PATTERN = Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$")

    fun isValidMac(text: String): Boolean = MAC_PATTERN.matches(text.trim())

    fun record(mac: String, name: String?): NdefRecord = NdefRecord.mime(MIME_TYPE, encode(mac, name))

    /** [mac] must satisfy [isValidMac]. */
    fun encode(mac: String, name: String?): Bytes {
        require(isValidMac(mac)) { "Not a Bluetooth MAC address: $mac" }
        val octets = mac.trim().split(':', '-').map { it.toInt(16) }
        val nameBytes = name?.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
        val eir = if (nameBytes == null) Bytes.EMPTY else Bytes.of(nameBytes.size + 1, EIR_COMPLETE_NAME) + Bytes.copyOf(nameBytes)
        val total = LENGTH_FIELD + MAC_BYTES + eir.size
        return Bytes.of(total and 0xFF, total ushr 8) + Bytes.of(*octets.reversed().toIntArray()) + eir
    }

    /** Null when the declared length disagrees with the data or the MAC is cut short. */
    fun parse(payload: Bytes): BluetoothAddress? {
        if (payload.size < LENGTH_FIELD + MAC_BYTES) return null
        val declared = payload[0] or (payload[1] shl 8)
        if (declared != payload.size) return null

        val mac = (0 until MAC_BYTES).map { payload[LENGTH_FIELD + MAC_BYTES - 1 - it] }
            .joinToString(":") { "%02X".format(it) }
        return BluetoothAddress(mac, readName(payload, LENGTH_FIELD + MAC_BYTES))
    }

    /** Walks the EIR structures (length, type, data) and returns the first local name. */
    private fun readName(payload: Bytes, start: Int): String? {
        var pos = start
        while (pos < payload.size) {
            val length = payload[pos]
            if (length == 0 || pos + 1 + length > payload.size) return null
            val type = payload[pos + 1]
            if (type == EIR_COMPLETE_NAME || type == EIR_SHORTENED_NAME) {
                return String(payload.slice(pos + 2, pos + 1 + length).toByteArray(), Charsets.UTF_8)
            }
            pos += 1 + length
        }
        return null
    }
}
