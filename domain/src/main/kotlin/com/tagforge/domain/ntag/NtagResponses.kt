package com.tagforge.domain.ntag

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.tag.TagError

/** Decoders for NTAG21x responses. NAKs are 4-bit codes delivered as a single byte. */
object NtagResponses {
    private const val ACK = 0x0A
    private val NAK_CODES = setOf(0x0, 0x1, 0x4, 0x5)

    private const val VERSION_LENGTH = 8
    private const val VENDOR_NXP = 0x04
    private const val PRODUCT_TYPE_NTAG = 0x04
    private const val VENDOR_INDEX = 1
    private const val PRODUCT_TYPE_INDEX = 2
    private const val STORAGE_SIZE_INDEX = 6

    /** Maps a GET_VERSION response to an NTAG21x model; null for anything else (never an error). */
    fun parseVersion(response: Bytes): NtagModel? {
        if (response.size != VERSION_LENGTH) return null
        if (response[VENDOR_INDEX] != VENDOR_NXP || response[PRODUCT_TYPE_INDEX] != PRODUCT_TYPE_NTAG) return null
        return NtagModel.fromVersionSize(response[STORAGE_SIZE_INDEX])
    }

    /** True when [response] is a lone NAK code. */
    fun nakCode(response: Bytes): Int? = if (response.size == 1 && response[0] in NAK_CODES) response[0] else null

    fun checkAck(response: Bytes): TagResult<Unit> {
        nakCode(response)?.let { return Outcome.Err(TagError.Nak(it)) }
        if (response.size != 1) return Outcome.Err(TagError.Malformed("Unexpected response length ${response.size}"))
        if (response[0] != ACK) {
            return Outcome.Err(TagError.Malformed("Unexpected ACK byte 0x%02X".format(response[0])))
        }
        return Outcome.Ok(Unit)
    }

    fun checkData(response: Bytes, expectedLength: Int): TagResult<Bytes> {
        nakCode(response)?.let { return Outcome.Err(TagError.Nak(it)) }
        if (response.size != expectedLength) {
            return Outcome.Err(TagError.Malformed("Expected $expectedLength bytes but received ${response.size}"))
        }
        return Outcome.Ok(response)
    }
}
