package com.tagforge.domain.fakes

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.iso15693.Iso15693
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagTech

/**
 * In-memory ISO 15693 tag (ICODE SLIX-like, or ST25DV-like with a bigger block count). Answers Get System Info (2Bh)
 * and Read Single Block (20h); anything else, including every write command, is answered "command not supported", so
 * a test can prove Tag Forge never writes raw to a 15693 tag. Enforces `maxTransceiveLength` like [FakeNtag21x].
 *
 * [uid] is in TRANSMISSION order, like Android's `Tag.getId()`: manufacturer at index 6, `E0` at index 7.
 */
class FakeIso15693(
    val uid: Bytes = ICODE_UID,
    val blockCount: Int = 80,
    val blockSize: Int = 4,
    val dsfId: Int = 0x00,
    val afi: Int = 0x00,
    /** Tags without Get System Info answer with the "not supported" error flag. */
    val supportsSystemInfo: Boolean = true,
    /** When set, Get System Info gets no answer and the link drops until [reconnect] (old tags / some controllers). */
    val dropsLinkOnSystemInfo: Boolean = false,
    /** First block that answers "security status" errors (a locked area), or null when everything reads. */
    val unreadableFrom: Int? = null,
    override val maxTransceiveLength: Int = 253,
) : TagTransceiver {

    override val tech: TagTech = TagTech.NFC_V

    private val blocks: Array<ByteArray> = Array(blockCount) { ByteArray(blockSize) }
    private val log = mutableListOf<Bytes>()
    private var linkDropped = false
    private var lost = false
    private var pendingFailure: TagError? = null

    var reconnectCount: Int = 0
        private set
    var transceiveCount: Int = 0
        private set

    val commandLog: List<Bytes> get() = log.toList()

    /** Frames that are not Get System Info or Read Single Block: must stay empty, Tag Forge never writes raw. */
    val otherCommands: List<Bytes> get() = log.filter { it[1] != Iso15693.GET_SYSTEM_INFO && it[1] != Iso15693.READ_SINGLE_BLOCK }

    fun block(index: Int): Bytes = Bytes.copyOf(blocks[index])

    fun setBlock(index: Int, data: Bytes) {
        require(data.size == blockSize)
        blocks[index] = data.toByteArray()
    }

    fun loseTag() {
        lost = true
    }

    fun failNext(error: TagError) {
        pendingFailure = error
    }

    override suspend fun reconnect(): TagResult<Unit> {
        if (lost) return Outcome.Err(TagError.TagLost)
        reconnectCount++
        linkDropped = false
        return Outcome.Ok(Unit)
    }

    override suspend fun transceive(command: Bytes): TagResult<Bytes> {
        transceiveCount++
        if (lost || linkDropped) return Outcome.Err(TagError.TagLost)
        pendingFailure?.let {
            pendingFailure = null
            return Outcome.Err(it)
        }
        if (command.size > maxTransceiveLength) return Outcome.Err(TagError.Io("Transceive length exceeds supported maximum"))
        if (command.size < 2 + Iso15693.UID_SIZE || command.slice(2, 2 + Iso15693.UID_SIZE) != uid) {
            return Outcome.Err(TagError.Io("No answer: addressed frame for another tag"))
        }
        log += command
        val response = when (command[1]) {
            Iso15693.GET_SYSTEM_INFO -> if (dropsLinkOnSystemInfo) return dropLink() else systemInfo()
            Iso15693.READ_SINGLE_BLOCK -> readSingle(command)
            else -> error(COMMAND_NOT_SUPPORTED)
        }
        if (response.size > maxTransceiveLength) return Outcome.Err(TagError.Io("Transceive response exceeds supported maximum"))
        return Outcome.Ok(response)
    }

    private fun dropLink(): TagResult<Bytes> {
        linkDropped = true
        return Outcome.Err(TagError.TagLost)
    }

    private fun systemInfo(): Bytes {
        if (!supportsSystemInfo) return error(COMMAND_NOT_SUPPORTED)
        return Bytes.of(0x00, INFO_FLAGS) + uid + Bytes.of(dsfId, afi, minOf(blockCount, MAX_REPORTED_BLOCKS) - 1, blockSize - 1, IC_REFERENCE)
    }

    private fun readSingle(command: Bytes): Bytes {
        val block = command[2 + Iso15693.UID_SIZE]
        if (block >= blockCount) return error(BLOCK_NOT_AVAILABLE)
        if (unreadableFrom?.let { block >= it } == true) return error(SECURITY_ERROR)
        return Bytes.of(0x00) + Bytes.copyOf(blocks[block])
    }

    private fun error(code: Int) = Bytes.of(0x01, code)

    companion object {
        /** E0 04 01 ... in transmission order: IC manufacturer NXP. */
        val ICODE_UID: Bytes = Bytes.of(0x11, 0x22, 0x33, 0x44, 0x55, 0x01, 0x04, 0xE0)

        /** E0 02 ... in transmission order: IC manufacturer STMicroelectronics. */
        val ST_UID: Bytes = Bytes.of(0x66, 0x77, 0x88, 0x99, 0xAA, 0xBB, 0x02, 0xE0)

        /** Get System Info has a 1-byte block count: bigger tags need the extended command, which Tag Forge does not use. */
        private const val MAX_REPORTED_BLOCKS = 256
        private const val INFO_FLAGS = 0x0F
        private const val IC_REFERENCE = 0x03
        private const val COMMAND_NOT_SUPPORTED = 0x01
        private const val BLOCK_NOT_AVAILABLE = 0x10
        private const val SECURITY_ERROR = 0x0F
    }
}
