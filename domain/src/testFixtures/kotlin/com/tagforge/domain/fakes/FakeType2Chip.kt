package com.tagforge.domain.fakes

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagTech
import com.tagforge.domain.type2.Type2Chip

/**
 * In-memory MIFARE Ultralight / Ultralight C / Ultralight EV1 / NTAG210 / NTAG212 following the NXP datasheets
 * (MF0ICU1, MF0ICU2, MF0ULX1, NTAG210/212). Enforces `maxTransceiveLength` like [FakeNtag21x] and the datasheet quirks
 * that matter to Tag Forge:
 *  - Ultralight and Ultralight C do not know GET_VERSION or FAST_READ. The chip stays silent and, like many NFC
 *    controllers, the link drops: the NEXT commands fail with TagLost until [reconnect] re-selects the tag.
 *  - page 3 of an Ultralight starts all zero (OTP) and writes are OR-ed into it; pages 0-1 are never writable.
 *  - READ answers 4 pages and rolls over past the last page; a start page beyond the memory is NAKed.
 */
class FakeType2Chip(
    val chip: Type2Chip,
    val uid: Bytes = DEFAULT_UID,
    override val maxTransceiveLength: Int = 253,
) : TagTransceiver {

    override val tech: TagTech = TagTech.NFC_A

    private val pages: Array<ByteArray> = blankPages(chip, uid)
    private var linkDropped = false
    private var lost = false
    private var loseAfterWrites: Int? = null
    private var pendingFailure: TagError? = null
    private val log = mutableListOf<Bytes>()
    private val written = mutableListOf<Int>()

    var transceiveCount: Int = 0
        private set
    var writeCount: Int = 0
        private set
    var reconnectCount: Int = 0
        private set

    val commandLog: List<Bytes> get() = log.toList()
    val writtenPages: List<Int> get() = written.toList()
    val writeCommands: List<Bytes> get() = log.filter { it[0] == NtagCommands.WRITE }

    fun page(index: Int): Bytes = Bytes.copyOf(pages[index])

    fun snapshot(): List<Bytes> = pages.map { Bytes.copyOf(it) }

    /** Test setup: bypasses every protection and does not count as a write. */
    fun setPage(index: Int, data: Bytes) {
        require(data.size == NtagCommands.PAGE_SIZE)
        pages[index] = data.toByteArray()
    }

    fun loseTagAfter(writes: Int) {
        loseAfterWrites = writes
        if (writes == 0) lost = true
    }

    fun failNext(error: TagError) {
        pendingFailure = error
    }

    /** True when page 3 holds an NFC Forum capability container. */
    val hasCapabilityContainer: Boolean get() = pages[CC_PAGE][0].toInt() and 0xFF == 0xE1

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
        if (command.isEmpty()) return Outcome.Ok(NAK)
        if (command.size > maxTransceiveLength) return Outcome.Err(TagError.Io("Transceive length exceeds supported maximum"))
        log += command
        val response = when (command[0]) {
            NtagCommands.GET_VERSION -> if (chip.versionSize != null) version() else return dropLink()
            NtagCommands.READ -> read(command)
            NtagCommands.FAST_READ -> if (chip.supportsFastRead) fastRead(command) else return dropLink()
            NtagCommands.WRITE -> write(command)
            else -> NAK
        }
        if (response.size > maxTransceiveLength) return Outcome.Err(TagError.Io("Transceive response exceeds supported maximum"))
        return Outcome.Ok(response)
    }

    private fun dropLink(): TagResult<Bytes> {
        linkDropped = true
        return Outcome.Err(TagError.TagLost)
    }

    private fun version(): Bytes = Bytes.of(0x00, 0x04, chip.versionProductType!!, 0x01, 0x01, 0x00, chip.versionSize!!, 0x03)

    private fun read(command: Bytes): Bytes {
        if (command.size != 2 || command[1] >= chip.totalPages) return NAK
        return Bytes.concat((0 until 4).map { Bytes.copyOf(pages[(command[1] + it) % chip.totalPages]) })
    }

    private fun fastRead(command: Bytes): Bytes {
        if (command.size != 3 || command[1] > command[2] || command[2] >= chip.totalPages) return NAK
        return Bytes.concat((command[1]..command[2]).map { Bytes.copyOf(pages[it]) })
    }

    private fun write(command: Bytes): Bytes {
        val target = command[1]
        if (command.size != 2 + NtagCommands.PAGE_SIZE || target < 2 || target >= chip.totalPages) return NAK
        val data = command.slice(2).toByteArray()
        when {
            target == 2 -> for (i in 2..3) pages[2][i] = (pages[2][i].toInt() or data[i].toInt()).toByte()
            target == CC_PAGE -> {
                if (staticLock() and L_CC != 0) return NAK
                for (i in 0 until 4) pages[CC_PAGE][i] = (pages[CC_PAGE][i].toInt() or data[i].toInt()).toByte()
            }
            target in 4..15 && staticLock() and (1 shl target) != 0 -> return NAK
            else -> pages[target] = data
        }
        writeCount++
        written += target
        if (loseAfterWrites?.let { writeCount >= it } == true) lost = true
        return ACK
    }

    private fun staticLock(): Int = (pages[2][2].toInt() and 0xFF) or ((pages[2][3].toInt() and 0xFF) shl 8)

    companion object {
        val DEFAULT_UID: Bytes = Bytes.of(0x04, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
        private val ACK = Bytes.of(0x0A)
        private val NAK = Bytes.of(0x00)
        private const val CC_PAGE = 3
        private const val L_CC = 0x0008

        private fun blankPages(chip: Type2Chip, uid: Bytes): Array<ByteArray> {
            val pages = Array(chip.totalPages) { ByteArray(NtagCommands.PAGE_SIZE) }
            pages[0] = byteArrayOf(uid[0].toByte(), uid[1].toByte(), uid[2].toByte(), (0x88 xor uid[0] xor uid[1] xor uid[2]).toByte())
            pages[1] = byteArrayOf(uid[3].toByte(), uid[4].toByte(), uid[5].toByte(), uid[6].toByte())
            pages[2] = byteArrayOf((uid[3] xor uid[4] xor uid[5] xor uid[6]).toByte(), 0x48, 0, 0)
            if (chip == Type2Chip.NTAG210 || chip == Type2Chip.NTAG212) {
                pages[CC_PAGE] = chip.blankCapabilityContainer.toByteArray()
                val factory = (chip.factoryLockControlTlv ?: Bytes.EMPTY) + Bytes.of(0x03, 0x00, 0xFE)
                val padded = factory.toByteArray().copyOf(((factory.size + 3) / 4) * 4)
                for (i in padded.indices step 4) pages[4 + i / 4] = padded.copyOfRange(i, i + 4)
            }
            return pages
        }
    }
}
