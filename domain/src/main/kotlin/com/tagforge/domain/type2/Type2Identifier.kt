package com.tagforge.domain.type2

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.UltralightType

/** Which Type 2 chip answered. [Unknown] is a Type 2 tag whose GET_VERSION this app does not recognise. */
sealed interface Type2Identity {
    data class Ntag(val model: NtagModel) : Type2Identity
    data class Other(val chip: Type2Chip) : Type2Identity

    /** NTAG I2C (NT3H1101/1201): recognised so it is never mistaken for an NTAG216; handled as a generic NDEF tag. */
    data class NtagI2c(val storageSize: Int) : Type2Identity
}

/**
 * Detects the Type 2 chip with GET_VERSION (60h).
 *
 * MIFARE Ultralight and Ultralight C do not know GET_VERSION: the chip answers with a NAK or stays silent, and many NFC
 * controllers drop the RF link when that happens. So when Android already told us the tag is a `MifareUltralight`
 * ([androidUltralightType] != null), a failed GET_VERSION is followed by a clean [TagTransceiver.reconnect] and the
 * chip is named after the type Android reported. Every other tag keeps the old rule: only a vanished tag is an error.
 */
object Type2Identifier {

    private const val VERSION_LENGTH = 8
    private const val VENDOR_NXP = 0x04
    private const val PRODUCT_ULTRALIGHT = 0x03
    private const val PRODUCT_NTAG = 0x04
    private const val SUBTYPE_I2C = 0x05
    private const val SUBTYPE_INDEX = 3
    private const val PRODUCT_INDEX = 2
    private const val SIZE_INDEX = 6
    private const val CC_PAGE = 3
    private const val CC_MAGIC = 0xE1
    private const val CC_READ_SIZE = 16
    private const val CC_SIZE_UNIT = 8

    suspend fun identify(transceiver: TagTransceiver, androidUltralightType: UltralightType? = null): TagResult<Type2Identity?> {
        val answered = transceiver.transceive(NtagCommands.getVersion())
        if (answered is Outcome.Ok) {
            parseVersion(answered.value)?.let { return Outcome.Ok(it) }
            // A well-formed answer from a chip this app does not know is not an Ultralight: report "unknown".
            if (answered.value.size == VERSION_LENGTH) return Outcome.Ok(null)
        } else if ((answered as Outcome.Err).error == TagError.Cancelled) {
            return answered
        } else if (androidUltralightType == null) {
            return when (answered.error) {
                TagError.TagLost, TagError.Timeout -> answered
                else -> Outcome.Ok(null)
            }
        }
        if (androidUltralightType == null) return Outcome.Ok(null)
        // The refused command (NAK, silence or a dropped link) may have left the tag deselected: start clean.
        return when (val back = transceiver.reconnect()) {
            is Outcome.Err -> back
            is Outcome.Ok -> confirmFallback(transceiver, androidUltralightType)
        }
    }

    /**
     * Android's Ultralight type is only a guess for a chip that did not answer GET_VERSION: an NTAG whose command failed
     * once, or an NTAG203, are also "Ultralight" to Android. The capability container tells them apart: one that
     * advertises more NDEF memory than the guessed chip has means a bigger chip. A transient failure is retried once.
     */
    private suspend fun confirmFallback(transceiver: TagTransceiver, type: UltralightType): TagResult<Type2Identity?> {
        val guess = fallback(type) as? Type2Identity.Other ?: return Outcome.Ok(null)
        val cc = when (val read = transceiver.transceive(NtagCommands.read(CC_PAGE))) {
            is Outcome.Ok -> read.value
            is Outcome.Err -> return if (read.error == TagError.TagLost || read.error == TagError.Cancelled || read.error == TagError.Timeout) read else Outcome.Ok(guess)
        }
        val advertised = if (cc.size == CC_READ_SIZE && cc[0] == CC_MAGIC) cc[2] * CC_SIZE_UNIT else 0
        if (advertised <= guess.chip.ccNdefSize) return Outcome.Ok(guess)
        val retry = transceiver.transceive(NtagCommands.getVersion())
        return Outcome.Ok((retry as? Outcome.Ok)?.let { parseVersion(it.value) })
    }

    /** Maps a GET_VERSION answer; null for a NAK, a short answer or a chip this app does not know. */
    fun parseVersion(response: Bytes): Type2Identity? {
        if (response.size != VERSION_LENGTH || response[1] != VENDOR_NXP) return null
        val product = response[PRODUCT_INDEX]
        val size = response[SIZE_INDEX]
        if (product == PRODUCT_NTAG && response[SUBTYPE_INDEX] == SUBTYPE_I2C) return Type2Identity.NtagI2c(size)
        if (product == PRODUCT_NTAG) NtagModel.fromVersionSize(size)?.let { return Type2Identity.Ntag(it) }
        return Type2Chip.fromVersion(product, size)?.let(Type2Identity::Other)
    }

    private fun fallback(type: UltralightType?): Type2Identity? = when (type) {
        UltralightType.ULTRALIGHT -> Type2Identity.Other(Type2Chip.ULTRALIGHT)
        UltralightType.ULTRALIGHT_C -> Type2Identity.Other(Type2Chip.ULTRALIGHT_C)
        else -> null
    }
}
