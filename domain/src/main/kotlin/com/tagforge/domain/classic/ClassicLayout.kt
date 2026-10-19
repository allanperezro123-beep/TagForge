package com.tagforge.domain.classic

import com.tagforge.domain.common.Bytes

/**
 * Sector and block arithmetic of the MIFARE Classic family. Mini has 5 sectors of 4 blocks, 1K 16 sectors of 4 blocks,
 * 2K 32 sectors of 4 blocks and 4K 32 sectors of 4 blocks followed by 8 sectors of 16 blocks. Blocks are 16 bytes, the
 * LAST block of every sector is its trailer (Key A, access bits, Key B) and block 0 is the read-only manufacturer block.
 */
class ClassicLayout private constructor(val sectorCount: Int) {

    val blockCount: Int get() = if (sectorCount <= SMALL_SECTORS) sectorCount * SMALL_BLOCKS else SMALL_SECTORS * SMALL_BLOCKS + (sectorCount - SMALL_SECTORS) * LARGE_BLOCKS

    val sizeBytes: Int get() = blockCount * BLOCK_SIZE

    fun firstBlockOf(sector: Int): Int {
        requireSector(sector)
        return if (sector < SMALL_SECTORS) sector * SMALL_BLOCKS else SMALL_SECTORS * SMALL_BLOCKS + (sector - SMALL_SECTORS) * LARGE_BLOCKS
    }

    fun blocksIn(sector: Int): Int {
        requireSector(sector)
        return if (sector < SMALL_SECTORS) SMALL_BLOCKS else LARGE_BLOCKS
    }

    fun trailerOf(sector: Int): Int = firstBlockOf(sector) + blocksIn(sector) - 1

    fun sectorOf(block: Int): Int {
        require(block in 0 until blockCount) { "Block out of range: $block" }
        return if (block < SMALL_SECTORS * SMALL_BLOCKS) block / SMALL_BLOCKS else SMALL_SECTORS + (block - SMALL_SECTORS * SMALL_BLOCKS) / LARGE_BLOCKS
    }

    fun isTrailer(block: Int): Boolean = trailerOf(sectorOf(block)) == block

    fun isManufacturerBlock(block: Int): Boolean = block == 0

    /** The blocks Tag Forge may ever write: every data block except block 0. Never a trailer. */
    fun writableBlocksOf(sector: Int): IntRange {
        val first = maxOf(firstBlockOf(sector), 1)
        return first until trailerOf(sector)
    }

    private fun requireSector(sector: Int) = require(sector in 0 until sectorCount) { "Sector out of range: $sector" }

    override fun equals(other: Any?): Boolean = other is ClassicLayout && other.sectorCount == sectorCount

    override fun hashCode(): Int = sectorCount

    override fun toString(): String = "ClassicLayout($sectorCount sectors)"

    companion object {
        const val BLOCK_SIZE = 16
        private const val SMALL_SECTORS = 32
        private const val SMALL_BLOCKS = 4
        private const val LARGE_BLOCKS = 16

        val MINI = ClassicLayout(5)
        val CLASSIC_1K = ClassicLayout(16)
        val CLASSIC_2K = ClassicLayout(32)
        val CLASSIC_4K = ClassicLayout(40)

        /** Null for a sector count no MIFARE Classic has. */
        fun ofSectors(sectorCount: Int): ClassicLayout? = when (sectorCount) {
            5 -> MINI
            16 -> CLASSIC_1K
            32 -> CLASSIC_2K
            40 -> CLASSIC_4K
            else -> null
        }
    }
}

enum class ClassicKeyType { A, B }

/**
 * The only keys Tag Forge ever tries: the factory default and the two NFC Forum / MAD public keys. No dictionaries,
 * no brute force, no key recovery (anything else is out of scope and stays that way).
 */
object ClassicKeys {
    val FACTORY_DEFAULT: Bytes = Bytes.of(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF)

    /** Key A of the MAD sector (sector 0, and sector 16 on 4K cards). */
    val MAD: Bytes = Bytes.of(0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5)

    /** Key A of NFC Forum formatted data sectors. */
    val NDEF: Bytes = Bytes.of(0xD3, 0xF7, 0xD3, 0xF7, 0xD3, 0xF7)

    val ALL: List<Bytes> = listOf(FACTORY_DEFAULT, MAD, NDEF)
}
