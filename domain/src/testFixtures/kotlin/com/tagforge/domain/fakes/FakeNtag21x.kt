package com.tagforge.domain.fakes

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagTech

/**
 * In-memory NTAG213/215/216 following NXP datasheet Rev. 3.2 closely enough to test every flow on the JVM:
 * GET_VERSION, READ (with roll-over), FAST_READ, WRITE, AUTH0/PROT gating, plus fault injection.
 *
 * Fault hooks: [loseTagAfter] makes the tag vanish after N completed writes; [failNext] fails one command.
 */
class FakeNtag21x(
    val model: NtagModel,
    val uid: Bytes = DEFAULT_UID,
) : TagTransceiver {

    override val tech: TagTech = TagTech.NFC_A
    override val maxTransceiveLength: Int = 253

    private val pages: Array<ByteArray> = blankPages(model, uid)

    /** True after a successful PWD_AUTH (tests may also set it directly); cleared by [powerCycle]. */
    var authenticated: Boolean = false

    /** Negative PWD_AUTH attempts counted since the last success (only counted while AUTHLIM > 0). */
    var authFailures: Int = 0
        private set

    /** What READ_SIG returns; the real value is an ECC signature of the UID (32 bytes). */
    var signature: Bytes = Bytes.copyOf(ByteArray(NtagCommands.SIGNATURE_SIZE) { (0xA0 + it).toByte() })

    /** What READ_CNT returns (3 bytes). */
    var nfcCounter: Bytes = Bytes.of(0x00, 0x00, 0x00)

    /** CFGLCK only takes effect after a power cycle (datasheet 8.5.7): this latch is what actually blocks CFG writes. */
    private var cfgLockLatched: Boolean = (pages[model.cfg1][0].toInt() and CFGLCK_BIT) != 0

    private val log = mutableListOf<Bytes>()

    /** Every command that reached the tag, in order, including refused ones. */
    val commandLog: List<Bytes> get() = log.toList()

    /** Only the WRITE (A2) commands of [commandLog]. */
    val writeCommands: List<Bytes> get() = log.filter { it[0] == NtagCommands.WRITE }

    /** The field drops and returns: authentication is forgotten and a pending CFGLCK becomes active. */
    fun powerCycle() {
        authenticated = false
        cfgLockLatched = (pages[model.cfg1][0].toInt() and CFGLCK_BIT) != 0
    }

    /** Simulates tags that NAK GET_VERSION (e.g. Ultralight look-alikes). */
    var getVersionNaks: Boolean = false

    var transceiveCount: Int = 0
        private set
    var writeCount: Int = 0
        private set
    private val written = mutableListOf<Int>()
    val writtenPages: List<Int> get() = written.toList()

    private var loseAfterWrites: Int? = null
    private var lost = false
    private var pendingFailure: TagError? = null
    private var delayedFailure: Pair<Int, TagError>? = null

    val pageCount: Int get() = model.totalPages

    fun page(index: Int): Bytes = Bytes.copyOf(pages[index])

    /** Test setup: bypasses every protection and does not count as a write. */
    fun setPage(index: Int, data: Bytes) {
        require(data.size == NtagCommands.PAGE_SIZE) { "A page is ${NtagCommands.PAGE_SIZE} bytes" }
        pages[index] = data.toByteArray()
    }

    /** All pages in order, for equality checks between tags. */
    fun snapshot(): List<Bytes> = pages.map { Bytes.copyOf(it) }

    fun loseTagAfter(writes: Int) {
        require(writes >= 0)
        loseAfterWrites = writes
        if (writes == 0) lost = true
    }

    fun failNext(error: TagError) {
        pendingFailure = error
    }

    /** Lets [transceives] more commands through, then fails exactly one with [error]. */
    fun failNextAfter(transceives: Int, error: TagError) {
        require(transceives >= 0)
        delayedFailure = transceiveCount + transceives + 1 to error
    }

    override suspend fun transceive(command: Bytes): TagResult<Bytes> {
        transceiveCount++
        if (lost) return Outcome.Err(TagError.TagLost)
        delayedFailure?.let { (at, error) ->
            if (transceiveCount == at) {
                delayedFailure = null
                return Outcome.Err(error)
            }
        }
        pendingFailure?.let {
            pendingFailure = null
            return Outcome.Err(it)
        }
        if (command.isEmpty()) return Outcome.Ok(NAK_INVALID_ARGUMENT)
        log += command
        return Outcome.Ok(
            when (command[0]) {
                NtagCommands.GET_VERSION -> getVersion()
                NtagCommands.READ -> read(command)
                NtagCommands.FAST_READ -> fastRead(command)
                NtagCommands.WRITE -> write(command)
                NtagCommands.PWD_AUTH -> pwdAuth(command)
                NtagCommands.READ_SIG -> readSig(command)
                NtagCommands.READ_CNT -> readCnt(command)
                else -> NAK_INVALID_ARGUMENT
            },
        )
    }

    // ---- password, signature, counter (datasheet 8.8, 10.5-10.7) --------------------------------------------

    private val authLimit: Int get() = pages[model.cfg1][0].toInt() and AUTHLIM_MASK

    /** PWD_AUTH: PACK on success; NAK 0h on a wrong password; NAK 4h forever once AUTHLIM negative attempts were counted. */
    private fun pwdAuth(command: Bytes): Bytes {
        if (command.size != 1 + NtagCommands.PASSWORD_SIZE) return NAK_INVALID_ARGUMENT
        val limit = authLimit
        if (limit > 0 && authFailures >= limit) return NAK_COUNTER_OVERFLOW
        if (command.slice(1) != Bytes.copyOf(pages[model.pwd])) {
            if (limit > 0) authFailures++
            return NAK_INVALID_ARGUMENT
        }
        authFailures = 0
        authenticated = true
        return Bytes.copyOf(pages[model.pack]).slice(0, NtagCommands.PACK_SIZE)
    }

    private fun readSig(command: Bytes): Bytes = if (command == NtagCommands.readSig()) signature else NAK_INVALID_ARGUMENT

    private fun readCnt(command: Bytes): Bytes {
        if (command != NtagCommands.readCnt()) return NAK_INVALID_ARGUMENT
        val passwordProtected = (pages[model.cfg1][0].toInt() and NFC_CNT_PWD_PROT_BIT) != 0
        return if (passwordProtected && !authenticated) NAK_INVALID_ARGUMENT else nfcCounter
    }

    /** PWD and PACK are write-only: reads answer 00 bytes whatever is stored (datasheet 8.8.1). */
    private fun readable(page: Int): Bytes =
        if (page == model.pwd || page == model.pack) Bytes.of(0, 0, 0, 0) else page(page)

    // ---- protection state ---------------------------------------------------------------------

    private val auth0: Int get() = pages[model.cfg0][3].toInt() and 0xFF
    private val readProtected: Boolean get() = (pages[model.cfg1][0].toInt() and PROT_BIT) != 0

    /** First page that is blocked for reading, or null when everything is readable. */
    private fun readLimit(): Int? = if (!authenticated && readProtected && auth0 <= model.lastPage) auth0 else null

    /** First page that is blocked for writing, or null. */
    private fun writeLimit(): Int? = if (!authenticated && auth0 <= model.lastPage) auth0 else null

    // ---- commands -----------------------------------------------------------------------------

    private fun getVersion(): Bytes =
        if (getVersionNaks) NAK_INVALID_ARGUMENT else Bytes.of(0x00, 0x04, 0x04, 0x02, 0x01, 0x00, model.versionSize, 0x03)

    private fun read(command: Bytes): Bytes {
        if (command.size != 2) return NAK_INVALID_ARGUMENT
        val start = command[1]
        if (start > model.lastPage) return NAK_INVALID_ARGUMENT
        val limit = readLimit()
        if (limit != null && start >= limit) return NAK_INVALID_ARGUMENT

        val wrapAt = limit ?: model.totalPages
        val out = ArrayList<Bytes>(4)
        var p = start
        repeat(4) {
            out += readable(p)
            p++
            if (p >= wrapAt) p = 0
        }
        return Bytes.concat(out)
    }

    private fun fastRead(command: Bytes): Bytes {
        if (command.size != 3) return NAK_INVALID_ARGUMENT
        val start = command[1]
        val end = command[2]
        if (start > end || end > model.lastPage) return NAK_INVALID_ARGUMENT
        val limit = readLimit()
        if (limit != null && end >= limit) return NAK_INVALID_ARGUMENT
        return Bytes.concat((start..end).map(::readable))
    }

    private fun write(command: Bytes): Bytes {
        if (command.size != 2 + NtagCommands.PAGE_SIZE) return NAK_INVALID_ARGUMENT
        val target = command[1]
        if (target < FIRST_WRITABLE_PAGE || target > model.lastPage) return NAK_INVALID_ARGUMENT
        val limit = writeLimit()
        if (limit != null && target >= limit) return NAK_INVALID_ARGUMENT

        if (cfgLockLatched && (target == model.cfg0 || target == model.cfg1)) return NAK_INVALID_ARGUMENT

        val data = command.slice(2).toByteArray()
        when {
            target == STATIC_LOCK_PAGE -> orStaticLockBytes(data)
            target == CC_PAGE -> {
                if (isPageLocked(CC_PAGE)) return NAK_INVALID_ARGUMENT
                for (i in 0 until NtagCommands.PAGE_SIZE) pages[CC_PAGE][i] = (pages[CC_PAGE][i].toInt() or data[i].toInt()).toByte()
            }
            target == model.dynLock -> orDynamicLockBytes(data)
            isPageLocked(target) -> return NAK_INVALID_ARGUMENT
            else -> pages[target] = data
        }
        writeCount++
        written += target
        if (loseAfterWrites?.let { writeCount >= it } == true) lost = true
        return ACK
    }

    // ---- lock state (datasheet 8.5.2 to 8.5.4) ----------------------------------------------------------------

    /** True when the Capability Container says "read-only" (byte 3 != 0): what Android's `Ndef.isWritable` reflects. */
    val isNdefWriteLocked: Boolean get() = pages[CC_PAGE][3].toInt() != 0

    /** Whether a WRITE to [page] is refused by the static (pages 3-15) or dynamic (16 and up) lock bits. */
    fun isPageLocked(page: Int): Boolean = when {
        page == CC_PAGE -> staticLockBits() and L_CC != 0
        page in FIRST_STATIC_LOCKED_PAGE..LAST_STATIC_LOCKED_PAGE -> staticLockBits() and staticBitOf(page) != 0
        page in FIRST_DYNAMIC_LOCKED_PAGE..model.userEnd -> dynamicLockBits() and (1 shl dynamicGroupOf(page)) != 0
        else -> false
    }

    /** Lock bytes 0 and 1 of page 02 as one 16-bit vector (byte 0 in the low half). */
    private fun staticLockBits(): Int = (pages[STATIC_LOCK_PAGE][2].toInt() and 0xFF) or ((pages[STATIC_LOCK_PAGE][3].toInt() and 0xFF) shl 8)

    private fun dynamicLockBits(): Int = (pages[model.dynLock][0].toInt() and 0xFF) or ((pages[model.dynLock][1].toInt() and 0xFF) shl 8)

    /** L4..L7 are bits 4-7 of lock byte 0 and L8..L15 bits 0-7 of lock byte 1: in the 16-bit vector, bit n locks page n. */
    private fun staticBitOf(page: Int): Int = 1 shl page

    private fun dynamicGroupOf(page: Int): Int = (page - FIRST_DYNAMIC_LOCKED_PAGE) / model.dynamicLockGranularityPages

    /** Lock bytes and block-locking bits are OR-ed; a set block-locking bit freezes its group of lock bits. */
    private fun orStaticLockBytes(data: ByteArray) {
        val old = staticLockBits()
        var frozen = 0
        if (old and BL_CC != 0) frozen = frozen or L_CC
        if (old and BL_9_4 != 0) frozen = frozen or L_4_TO_9
        if (old and BL_15_10 != 0) frozen = frozen or L_10_TO_15
        val incoming = ((data[2].toInt() and 0xFF) or ((data[3].toInt() and 0xFF) shl 8)) and frozen.inv()
        val merged = old or incoming
        pages[STATIC_LOCK_PAGE][2] = merged.toByte()
        pages[STATIC_LOCK_PAGE][3] = (merged ushr 8).toByte()
    }

    /** RFUI bits stay 0 (per chip), byte 3 always reads BDh, block-locking bit k freezes lock groups 2k and 2k+1. */
    private fun orDynamicLockBytes(data: ByteArray) {
        val mask = DYNAMIC_LOCK_MASKS.getValue(model)
        val old = dynamicLockBits()
        val oldBlock = pages[model.dynLock][2].toInt() and 0xFF
        var frozen = 0
        for (k in 0 until BLOCK_LOCK_BITS) if (oldBlock and (1 shl k) != 0) frozen = frozen or (0b11 shl (2 * k))

        val incoming = (((data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)) and mask.vector) and frozen.inv()
        val merged = old or incoming
        pages[model.dynLock] = byteArrayOf(
            merged.toByte(),
            (merged ushr 8).toByte(),
            (oldBlock or (data[2].toInt() and mask.blockLock)).toByte(),
            DYNAMIC_LOCK_BYTE_3.toByte(),
        )
    }

    private class DynamicLockMask(val vector: Int, val blockLock: Int)

    companion object {
        val DEFAULT_UID: Bytes = Bytes.of(0x04, 0xA2, 0x3B, 0x1C, 0x5D, 0x6E, 0x80)

        private val ACK = Bytes.of(0x0A)
        private val NAK_INVALID_ARGUMENT = Bytes.of(0x00)
        private val NAK_COUNTER_OVERFLOW = Bytes.of(0x04)
        private const val AUTHLIM_MASK = 0x07
        private const val CFGLCK_BIT = 0x40
        private const val NFC_CNT_PWD_PROT_BIT = 0x08
        private const val FIRST_WRITABLE_PAGE = 2
        private const val STATIC_LOCK_PAGE = 2
        private const val CC_PAGE = 3
        private const val FIRST_STATIC_LOCKED_PAGE = 4
        private const val LAST_STATIC_LOCKED_PAGE = 15
        private const val FIRST_DYNAMIC_LOCKED_PAGE = 16
        private const val L_CC = 0x0008
        private const val BL_CC = 0x0001
        private const val BL_9_4 = 0x0002
        private const val BL_15_10 = 0x0004
        private const val L_4_TO_9 = 0x03F0
        private const val L_10_TO_15 = 0xFC00
        private const val BLOCK_LOCK_BITS = 7
        private const val DYNAMIC_LOCK_BYTE_3 = 0xBD

        /** Bits the chip actually implements in dynamic lock bytes 0-1 (lock vector) and byte 2 (block locking); the rest is RFUI. */
        private val DYNAMIC_LOCK_MASKS = mapOf(
            NtagModel.NTAG213 to DynamicLockMask(vector = 0x0FFF, blockLock = 0x3F),
            NtagModel.NTAG215 to DynamicLockMask(vector = 0x00FF, blockLock = 0x0F),
            NtagModel.NTAG216 to DynamicLockMask(vector = 0x3FFF, blockLock = 0x7F),
        )
        private const val PROT_BIT = 0x80
        private const val CASCADE_TAG = 0x88
        private const val INTERNAL_BYTE = 0x48

        private fun blankPages(model: NtagModel, uid: Bytes): Array<ByteArray> {
            require(uid.size == 7) { "NTAG21x has a 7-byte UID" }
            val pages = Array(model.totalPages) { ByteArray(NtagCommands.PAGE_SIZE) }
            val bcc0 = CASCADE_TAG xor uid[0] xor uid[1] xor uid[2]
            val bcc1 = uid[3] xor uid[4] xor uid[5] xor uid[6]
            pages[0] = byteArrayOf(uid[0].toByte(), uid[1].toByte(), uid[2].toByte(), bcc0.toByte())
            pages[1] = byteArrayOf(uid[3].toByte(), uid[4].toByte(), uid[5].toByte(), uid[6].toByte())
            pages[2] = byteArrayOf(bcc1.toByte(), INTERNAL_BYTE.toByte(), 0, 0)
            pages[3] = model.capabilityContainer.toByteArray()

            val factory = model.factoryNdefArea.toByteArray()
            for (i in factory.indices step NtagCommands.PAGE_SIZE) {
                pages[model.userStart + i / NtagCommands.PAGE_SIZE] = factory.copyOfRange(i, i + NtagCommands.PAGE_SIZE)
            }
            pages[model.dynLock] = byteArrayOf(0, 0, 0, 0xBD.toByte())
            pages[model.cfg0] = byteArrayOf(0, 0, 0, 0xFF.toByte())   // AUTH0 = FF: protection disabled
            return pages
        }
    }
}
