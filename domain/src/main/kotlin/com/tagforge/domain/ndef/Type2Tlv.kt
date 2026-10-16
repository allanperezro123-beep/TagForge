package com.tagforge.domain.ndef

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome

/** NFC Forum Type 2 Tag TLV framing around an NDEF message. */
object Type2Tlv {

    private const val TAG_NULL = 0x00
    private const val TAG_LOCK_CONTROL = 0x01
    private const val TAG_MEMORY_CONTROL = 0x02
    private const val TAG_NDEF = 0x03
    private const val TAG_PROPRIETARY = 0xFD
    private const val TAG_TERMINATOR = 0xFE
    private const val LONG_LENGTH_MARKER = 0xFF
    private const val MAX_SHORT_LENGTH = 254

    /**
     * Result of reading a TLV area.
     * @property message the bytes of the NDEF message (empty for an empty NDEF TLV)
     * @property leadingBytes bytes that precede the NDEF TLV (Lock/Memory Control TLVs, NULLs) and must be preserved
     */
    data class Area(val message: Bytes, val leadingBytes: Int)

    /** `03 <len> message FE`; [leading] TLVs (e.g. the NTAG213 Lock Control TLV) are kept in front. */
    fun wrap(message: Bytes, leading: Bytes = Bytes.EMPTY): Bytes {
        val length = if (message.size <= MAX_SHORT_LENGTH) {
            Bytes.of(message.size)
        } else {
            Bytes.of(LONG_LENGTH_MARKER, (message.size ushr 8) and 0xFF, message.size and 0xFF)
        }
        return leading + Bytes.of(TAG_NDEF) + length + message + Bytes.of(TAG_TERMINATOR)
    }

    fun unwrap(bytes: Bytes): Outcome<Area, NdefParseError> {
        var pos = 0
        while (pos < bytes.size) {
            val tag = bytes[pos]
            when (tag) {
                TAG_NULL -> pos += 1
                TAG_TERMINATOR -> return Outcome.Err(NdefParseError.NoNdefTlv)
                TAG_LOCK_CONTROL, TAG_MEMORY_CONTROL, TAG_PROPRIETARY, TAG_NDEF -> {
                    val (length, valueStart) = readLength(bytes, pos + 1) ?: return Outcome.Err(NdefParseError.Truncated)
                    if (valueStart + length > bytes.size) return Outcome.Err(NdefParseError.Truncated)
                    if (tag == TAG_NDEF) {
                        return Outcome.Ok(Area(bytes.slice(valueStart, valueStart + length), leadingBytes = pos))
                    }
                    pos = valueStart + length
                }
                else -> return Outcome.Err(NdefParseError.InvalidTlv(tag))
            }
        }
        return Outcome.Err(NdefParseError.NoNdefTlv)
    }

    /** Reads a 1- or 3-byte TLV length at [at]; returns (length, index of first value byte) or null if cut short. */
    private fun readLength(bytes: Bytes, at: Int): Pair<Int, Int>? {
        if (at >= bytes.size) return null
        if (bytes[at] != LONG_LENGTH_MARKER) return bytes[at] to at + 1
        if (at + 2 >= bytes.size) return null
        return ((bytes[at + 1] shl 8) or bytes[at + 2]) to at + 3
    }
}
