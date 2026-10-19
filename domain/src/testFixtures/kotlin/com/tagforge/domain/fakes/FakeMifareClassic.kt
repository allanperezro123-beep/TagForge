package com.tagforge.domain.fakes

import com.tagforge.domain.classic.ClassicKeyType
import com.tagforge.domain.classic.ClassicLayout
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ports.ClassicChannel
import com.tagforge.domain.tag.TagError

/**
 * In-memory MIFARE Classic (Mini, 1K, 2K or 4K) with real sector/trailer layout and key authentication: every sector
 * starts with the factory keys, a trailer is `KeyA(6) | access bits(4) | KeyB(6)` and Key A reads back as zeros.
 *
 * It is deliberately FRIENDLY to bad callers: writes to block 0 are refused (like a genuine card) but a write to a
 * sector trailer is ACCEPTED and recorded in [trailerWrites], so a test fails loudly if the domain guard ever lets one
 * through. Frames above [maxTransceiveLength] are refused like on a real controller.
 */
class FakeMifareClassic(
    override val layout: ClassicLayout = ClassicLayout.CLASSIC_1K,
    val uid: Bytes = Bytes.of(0x1A, 0x2B, 0x3C, 0x4D),
    val maxTransceiveLength: Int = 253,
) : ClassicChannel {

    private val blocks: Array<ByteArray> = Array(layout.blockCount) { ByteArray(ClassicLayout.BLOCK_SIZE) }
    private var authenticatedSector: Int? = null
    private var lost = false

    /** Blocks that refuse to be read even after a correct authentication (restrictive access bits). */
    val deniedReads = mutableSetOf<Int>()

    val authAttempts = mutableListOf<Triple<Int, Bytes, ClassicKeyType>>()
    val writeLog = mutableListOf<Int>()
    val trailerWrites: List<Int> get() = writeLog.filter { layout.isTrailer(it) }
    var reconnectLikeFailures: Int = 0
        private set

    init {
        blocks[0] = (uid + Bytes.of(uid[0] xor uid[1] xor uid[2] xor uid[3], 0x08, 0x04, 0x00)).toByteArray().copyOf(ClassicLayout.BLOCK_SIZE)
        for (sector in 0 until layout.sectorCount) setKeys(sector, FACTORY, FACTORY)
    }

    fun block(index: Int): Bytes = Bytes.copyOf(blocks[index])

    /** Test setup: bypasses authentication and does not count as a write. */
    fun setBlock(index: Int, data: Bytes) {
        require(data.size == ClassicLayout.BLOCK_SIZE)
        blocks[index] = data.toByteArray()
    }

    fun setKeys(sector: Int, keyA: Bytes, keyB: Bytes) {
        blocks[layout.trailerOf(sector)] = (keyA + ACCESS_BITS + keyB).toByteArray()
    }

    fun loseTag() {
        lost = true
    }

    override suspend fun authenticate(sector: Int, key: Bytes, type: ClassicKeyType): TagResult<Boolean> {
        if (lost) return Outcome.Err(TagError.TagLost)
        authAttempts += Triple(sector, key, type)
        val trailer = blocks[layout.trailerOf(sector)]
        val stored = Bytes.copyOf(if (type == ClassicKeyType.A) trailer.copyOfRange(0, 6) else trailer.copyOfRange(10, 16))
        authenticatedSector = if (stored == key) sector else null
        return Outcome.Ok(authenticatedSector != null)
    }

    override suspend fun readBlock(block: Int): TagResult<Bytes> {
        if (lost) return Outcome.Err(TagError.TagLost)
        if (ClassicLayout.BLOCK_SIZE > maxTransceiveLength) return Outcome.Err(TagError.Io("Transceive length exceeds supported maximum"))
        if (authenticatedSector != layout.sectorOf(block)) return Outcome.Err(TagError.Io("Sector not authenticated"))
        if (block in deniedReads) {
            authenticatedSector = null
            return Outcome.Err(TagError.Io("Read refused by the access bits"))
        }
        val data = blocks[block].copyOf()
        if (layout.isTrailer(block)) data.fill(0, 0, KEY_SIZE)   // Key A never reads back
        return Outcome.Ok(Bytes.copyOf(data))
    }

    override suspend fun writeBlock(block: Int, data: Bytes): TagResult<Unit> {
        if (lost) return Outcome.Err(TagError.TagLost)
        if (data.size + 1 > maxTransceiveLength) return Outcome.Err(TagError.Io("Transceive length exceeds supported maximum"))
        if (authenticatedSector != layout.sectorOf(block)) return Outcome.Err(TagError.Io("Sector not authenticated"))
        if (layout.isManufacturerBlock(block)) return Outcome.Err(TagError.Io("Block 0 is read-only"))
        writeLog += block
        blocks[block] = data.toByteArray()
        return Outcome.Ok(Unit)
    }

    companion object {
        val FACTORY: Bytes = Bytes.of(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF)
        private val ACCESS_BITS = Bytes.of(0xFF, 0x07, 0x80, 0x69)
        private const val KEY_SIZE = 6
    }
}
