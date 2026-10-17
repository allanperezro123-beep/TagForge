package com.tagforge.domain.ndef.spec

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ndef.NdefRecord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Authentication types of the WSC credential (attribute 0x1003). Codes are the on-tag values. */
@Serializable
enum class WifiAuth(val code: Int) {
    @SerialName("open") OPEN(0x0001),
    @SerialName("wpa") WPA_PSK(0x0002),
    @SerialName("wpa2") WPA2_PSK(0x0020),
    ;

    companion object {
        fun fromCode(code: Int): WifiAuth? = entries.firstOrNull { it.code == code }
    }
}

/** Encryption types of the WSC credential (attribute 0x100F). */
@Serializable
enum class WifiEncryption(val code: Int) {
    @SerialName("none") NONE(0x0001),
    @SerialName("tkip") TKIP(0x0004),
    @SerialName("aes") AES(0x0008),
    ;

    companion object {
        fun fromCode(code: Int): WifiEncryption? = entries.firstOrNull { it.code == code }
    }
}

/** [key] is null or empty for an open network. */
data class WifiCredentials(
    val ssid: String,
    val key: String?,
    val auth: WifiAuth,
    val encryption: WifiEncryption,
)

/**
 * Wi-Fi Simple Configuration (WSC) credential carried in an `application/vnd.wfa.wsc` MIME record.
 * Attributes are 2-byte id + 2-byte length + value, big-endian. Attribute ids are from the WFA WSC 2.0.x spec.
 */
object WifiWsc {

    const val MIME_TYPE = "application/vnd.wfa.wsc"

    private const val ATTR_VERSION = 0x104A
    private const val ATTR_CREDENTIAL = 0x100E
    private const val ATTR_NETWORK_INDEX = 0x1026
    private const val ATTR_SSID = 0x1045
    private const val ATTR_AUTH_TYPE = 0x1003
    private const val ATTR_ENCRYPTION_TYPE = 0x100F
    private const val ATTR_NETWORK_KEY = 0x1027
    private const val ATTR_MAC_ADDRESS = 0x1020

    private const val WSC_VERSION_1 = 0x10
    private const val NETWORK_INDEX = 0x01
    private const val MAC_BYTES = 6
    private const val MAX_ATTRIBUTE_LENGTH = 0xFFFF

    fun record(credentials: WifiCredentials): NdefRecord = NdefRecord.mime(MIME_TYPE, encode(credentials))

    fun encode(credentials: WifiCredentials): Bytes {
        val credential = Bytes.concat(
            listOf(
                attribute(ATTR_NETWORK_INDEX, Bytes.of(NETWORK_INDEX)),
                attribute(ATTR_SSID, utf8(credentials.ssid)),
                attribute(ATTR_AUTH_TYPE, u16(credentials.auth.code)),
                attribute(ATTR_ENCRYPTION_TYPE, u16(credentials.encryption.code)),
                attribute(ATTR_NETWORK_KEY, utf8(credentials.key.orEmpty())),
                attribute(ATTR_MAC_ADDRESS, Bytes.of(*IntArray(MAC_BYTES) { 0xFF })),
            ),
        )
        return attribute(ATTR_VERSION, Bytes.of(WSC_VERSION_1)) + attribute(ATTR_CREDENTIAL, credential)
    }

    /** Null when [payload] holds no complete credential. Unknown attributes, at either level, are skipped. */
    fun parse(payload: Bytes): WifiCredentials? {
        val credential = attributes(payload)?.firstOrNull { it.first == ATTR_CREDENTIAL }?.second ?: return null
        val fields = attributes(credential) ?: return null

        val ssid = fields.firstOrNull { it.first == ATTR_SSID }?.second?.let(::text) ?: return null
        val auth = fields.firstOrNull { it.first == ATTR_AUTH_TYPE }?.second?.let(::readU16)?.let(WifiAuth::fromCode)
            ?: return null
        val encryption = fields.firstOrNull { it.first == ATTR_ENCRYPTION_TYPE }?.second?.let(::readU16)
            ?.let(WifiEncryption::fromCode) ?: return null
        val key = fields.firstOrNull { it.first == ATTR_NETWORK_KEY }?.second?.let(::text)?.takeIf { it.isNotEmpty() }
        return WifiCredentials(ssid, key, auth, encryption)
    }

    /** Splits [bytes] into (id, value) pairs; null if an attribute runs past the end. */
    private fun attributes(bytes: Bytes): List<Pair<Int, Bytes>>? {
        val out = ArrayList<Pair<Int, Bytes>>()
        var pos = 0
        while (pos < bytes.size) {
            if (pos + 4 > bytes.size) return null
            val id = (bytes[pos] shl 8) or bytes[pos + 1]
            val length = (bytes[pos + 2] shl 8) or bytes[pos + 3]
            if (pos + 4 + length > bytes.size) return null
            out += id to bytes.slice(pos + 4, pos + 4 + length)
            pos += 4 + length
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private fun attribute(id: Int, value: Bytes): Bytes {
        require(value.size <= MAX_ATTRIBUTE_LENGTH) { "WSC attribute too long" }
        return Bytes.of(id ushr 8, id and 0xFF, value.size ushr 8, value.size and 0xFF) + value
    }

    private fun u16(value: Int): Bytes = Bytes.of(value ushr 8, value and 0xFF)

    private fun readU16(bytes: Bytes): Int? = if (bytes.size == 2) (bytes[0] shl 8) or bytes[1] else null

    private fun utf8(text: String): Bytes = Bytes.copyOf(text.toByteArray(Charsets.UTF_8))

    private fun text(bytes: Bytes): String = String(bytes.toByteArray(), Charsets.UTF_8)
}
