package com.tagforge.domain.iso15693

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.tag.NfcVDetails
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagInfo

/**
 * ISO/IEC 15693-3 frames (ICODE SLIX/SLIX2, ST25DV/ST25TV, ...). Pure builders and parsers, no I/O.
 *
 * ISO 15693 UIDs are 8 bytes. Android's `Tag.getId()` returns them in TRANSMISSION order (least significant byte
 * first), so the manufacturer byte is at index 6 and the `E0` prefix at index 7; the same array goes straight into an
 * addressed frame. This ordering is an assumption from field knowledge of Android's NfcV stack, pinned in one place
 * ([manufacturerCode]) so a phone that reports it the other way round needs a one-line change.
 */
object Iso15693 {
    /** Addressed request, high data rate. */
    const val FLAGS_ADDRESSED = 0x22
    const val READ_SINGLE_BLOCK = 0x20
    const val GET_SYSTEM_INFO = 0x2B
    const val UID_SIZE = 8

    /** Single-block commands carry a 1-byte block number. */
    const val MAX_BLOCKS_ADDRESSABLE = 256

    private const val ERROR_FLAG = 0x01
    private const val INFO_DSFID = 0x01
    private const val INFO_AFI = 0x02
    private const val INFO_MEMORY = 0x04
    private const val INFO_IC_REFERENCE = 0x08
    private const val BLOCK_SIZE_MASK = 0x1F

    /** ISO/IEC 7816-6 manufacturer codes seen on 15693 tags. */
    private val MANUFACTURERS = mapOf(0x02 to "STMicroelectronics", 0x04 to "NXP", 0x05 to "Infineon", 0x07 to "Texas Instruments", 0x16 to "EM Microelectronic")

    fun getSystemInfo(uid: Bytes): Bytes = Bytes.of(FLAGS_ADDRESSED, GET_SYSTEM_INFO) + checked(uid)

    fun readSingleBlock(uid: Bytes, block: Int): Bytes {
        require(block in 0 until MAX_BLOCKS_ADDRESSABLE) { "Block out of range: $block" }
        return Bytes.of(FLAGS_ADDRESSED, READ_SINGLE_BLOCK) + checked(uid) + Bytes.of(block)
    }

    /** IC manufacturer code of a UID as Android reports it, or null when it is not an `E0` ISO 15693 UID. */
    fun manufacturerCode(uid: Bytes): Int? = if (uid.size == UID_SIZE && uid[UID_SIZE - 1] == 0xE0) uid[UID_SIZE - 2] else null

    fun manufacturerName(uid: Bytes): String? = manufacturerCode(uid)?.let { MANUFACTURERS[it] }

    /** The UID the way people write it (most significant byte first, `E0` first). */
    fun displayUid(uid: Bytes): Bytes = Bytes.copyOf(uid.toByteArray().reversedArray())

    /** Get System Info answer; every field is optional because the tag only sends what its info flags announce. */
    data class SystemInfo(val dsfId: Int?, val afi: Int?, val blockCount: Int?, val blockSize: Int?, val icReference: Int?)

    fun parseSystemInfo(response: Bytes): SystemInfo? {
        if (response.size < 2 + UID_SIZE || response[0] and ERROR_FLAG != 0) return null
        val flags = response[1]
        var at = 2 + UID_SIZE
        fun next(width: Int): Bytes? = if (at + width <= response.size) response.slice(at, at + width).also { at += width } else null
        val dsfId = if (flags and INFO_DSFID != 0) next(1)?.get(0) else null
        val afi = if (flags and INFO_AFI != 0) next(1)?.get(0) else null
        val memory = if (flags and INFO_MEMORY != 0) next(2) else null
        val icReference = if (flags and INFO_IC_REFERENCE != 0) next(1)?.get(0) else null
        return SystemInfo(
            dsfId, afi,
            blockCount = memory?.let { it[0] + 1 },
            blockSize = memory?.let { (it[1] and BLOCK_SIZE_MASK) + 1 },
            icReference = icReference,
        )
    }

    /** The data of a Read Single Block answer; Err when the tag flagged an error (its code is in [TagError.Nak]). */
    fun blockData(response: Bytes): TagResult<Bytes> = when {
        response.isEmpty() -> Outcome.Err(TagError.Malformed("Empty ISO 15693 answer"))
        response[0] and ERROR_FLAG != 0 -> Outcome.Err(TagError.Nak(if (response.size > 1) response[1] else 0))
        else -> Outcome.Ok(response.slice(1))
    }

    /**
     * Asks the tag for its system information; Ok(null) when it does not support the command. A tag that stays silent
     * may have dropped the RF link, so a failed attempt is followed by a clean reconnect (a vanished tag stays TagLost).
     */
    suspend fun querySystemInfo(transceiver: TagTransceiver, uid: Bytes): TagResult<SystemInfo?> =
        when (val sent = transceiver.transceive(getSystemInfo(uid))) {
            is Outcome.Ok -> Outcome.Ok(parseSystemInfo(sent.value))
            is Outcome.Err -> if (sent.error == TagError.Cancelled) {
                sent
            } else {
                when (val back = transceiver.reconnect()) {
                    is Outcome.Err -> back
                    is Outcome.Ok -> Outcome.Ok(null)
                }
            }
        }

    /**
     * Completes [info] with what Get System Info reports (AFI, block count and size, IC reference). A tag that does not
     * answer leaves it unchanged. Only a vanished tag is an error.
     */
    suspend fun complete(info: TagInfo, transceiver: TagTransceiver): TagResult<TagInfo> {
        val system = when (val queried = querySystemInfo(transceiver, info.uid)) {
            is Outcome.Err -> return queried
            is Outcome.Ok -> queried.value ?: return Outcome.Ok(info)
        }
        val base = info.details.nfcV ?: NfcVDetails(dsfId = system.dsfId ?: 0, responseFlags = 0)
        val merged = base.copy(
            dsfId = system.dsfId ?: base.dsfId, afi = system.afi, blockCount = system.blockCount,
            blockSize = system.blockSize, icReference = system.icReference,
        )
        return Outcome.Ok(info.copy(details = info.details.copy(nfcV = merged)))
    }

    private fun checked(uid: Bytes): Bytes {
        require(uid.size == UID_SIZE) { "An ISO 15693 UID is $UID_SIZE bytes but got ${uid.size}" }
        return uid
    }
}
