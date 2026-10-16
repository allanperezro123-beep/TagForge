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

    /** Set by tests that simulate a successful PWD_AUTH (the command itself arrives in Phase 6). */
    var authenticated: Boolean = false

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

    override suspend fun transceive(command: Bytes): TagResult<Bytes> {
        transceiveCount++
        if (lost) return Outcome.Err(TagError.TagLost)
        pendingFailure?.let {
            pendingFailure = null
            return Outcome.Err(it)
        }
        if (command.isEmpty()) return Outcome.Ok(NAK_INVALID_ARGUMENT)
        return Outcome.Ok(
            when (command[0]) {
                NtagCommands.GET_VERSION -> getVersion()
                NtagCommands.READ -> read(command)
                NtagCommands.FAST_READ -> fastRead(command)
                NtagCommands.WRITE -> write(command)
                else -> NAK_INVALID_ARGUMENT
            },
        )
    }

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
            out += page(p)
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
        return Bytes.concat((start..end).map(::page))
    }

    private fun write(command: Bytes): Bytes {
        if (command.size != 2 + NtagCommands.PAGE_SIZE) return NAK_INVALID_ARGUMENT
        val target = command[1]
        if (target < FIRST_WRITABLE_PAGE || target > model.lastPage) return NAK_INVALID_ARGUMENT
        val limit = writeLimit()
        if (limit != null && target >= limit) return NAK_INVALID_ARGUMENT

        pages[target] = command.slice(2).toByteArray()
        writeCount++
        written += target
        if (loseAfterWrites?.let { writeCount >= it } == true) lost = true
        return ACK
    }

    companion object {
        val DEFAULT_UID: Bytes = Bytes.of(0x04, 0xA2, 0x3B, 0x1C, 0x5D, 0x6E, 0x80)

        private val ACK = Bytes.of(0x0A)
        private val NAK_INVALID_ARGUMENT = Bytes.of(0x00)
        private const val FIRST_WRITABLE_PAGE = 2
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
