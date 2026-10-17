package com.tagforge.domain.ndef.spec

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.Tnf
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

/**
 * [key] is null or empty for an open network (or when it was deliberately not stored, see [passwordState]).
 * The codes are the raw on-tag values so credentials with codes this app does not write itself (WPA/WPA2 mixed
 * 0x0022, WPA3-SAE 0x0040, TKIP+AES 0x000C, ...) still decode; [auth] / [encryption] are null for those.
 */
data class WifiCredentials(
    val ssid: String,
    val key: String?,
    val authCode: Int,
    val encryptionCode: Int,
) {
    constructor(ssid: String, key: String?, auth: WifiAuth, encryption: WifiEncryption) :
        this(ssid, key, auth.code, encryption.code)

    val auth: WifiAuth? get() = WifiAuth.fromCode(authCode)
    val encryption: WifiEncryption? get() = WifiEncryption.fromCode(encryptionCode)

    /** What the UI should say about the password: nothing needed, held back on purpose, or the value itself. */
    val passwordState: WifiPasswordState
        get() = when {
            !key.isNullOrEmpty() -> WifiPasswordState.Present(key)
            authCode == WifiAuth.OPEN.code -> WifiPasswordState.None
            else -> WifiPasswordState.NotStored
        }
}

sealed interface WifiPasswordState {
    /** Open network: there is no password. */
    data object None : WifiPasswordState

    /** A secured network whose key is absent, e.g. stripped from history. */
    data object NotStored : WifiPasswordState

    data class Present(val key: String) : WifiPasswordState
}

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
                attribute(ATTR_AUTH_TYPE, u16(credentials.authCode)),
                attribute(ATTR_ENCRYPTION_TYPE, u16(credentials.encryptionCode)),
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
        val auth = fields.firstOrNull { it.first == ATTR_AUTH_TYPE }?.second?.let(::readU16) ?: return null
        val encryption = fields.firstOrNull { it.first == ATTR_ENCRYPTION_TYPE }?.second?.let(::readU16) ?: return null
        val key = fields.firstOrNull { it.first == ATTR_NETWORK_KEY }?.second?.let(::text)?.takeIf { it.isNotEmpty() }
        return WifiCredentials(ssid, key, auth, encryption)
    }

    /** True for a MIME record whose media type is the WSC one (case-insensitive), whether or not its payload parses. */
    fun isWscRecord(record: NdefRecord): Boolean =
        record.tnf == Tnf.MIME && String(record.type.toByteArray(), Charsets.US_ASCII).equals(MIME_TYPE, ignoreCase = true)

    /**
     * [payload] with every Network Key (0x1027) value emptied, at any nesting level, independent of whether the
     * auth/encryption codes are ones this app knows. Bytes that do not walk as TLV cannot be trusted to be free of
     * a key, so they are dropped entirely (empty result) rather than stored.
     */
    fun redactNetworkKey(payload: Bytes): Bytes = stripKeys(payload) ?: Bytes.EMPTY

    private fun stripKeys(bytes: Bytes): Bytes? {
        if (bytes.isEmpty()) return bytes
        val attributes = attributes(bytes) ?: return null
        val rebuilt = attributes.map { (id, value) ->
            when (id) {
                ATTR_NETWORK_KEY -> attribute(id, Bytes.EMPTY)
                ATTR_CREDENTIAL -> attribute(id, stripKeys(value) ?: return null)
                else -> attribute(id, value)
            }
        }
        return Bytes.concat(rebuilt)
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
