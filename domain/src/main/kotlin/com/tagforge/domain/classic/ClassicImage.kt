package com.tagforge.domain.classic

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.MemoryDump
import com.tagforge.domain.ntag.PageAnnotation
import com.tagforge.domain.ntag.Region
import com.tagforge.domain.tag.TagError

/**
 * What could be read of a MIFARE Classic card with the public keys: every block of every sector one of them opened.
 * [lockedSectors] are the sectors no public key opened, [unreadableBlocks] the blocks a key opened but the access bits
 * refused to give.
 */
class ClassicImage(
    val layout: ClassicLayout,
    val blocks: Map<Int, Bytes>,
    val lockedSectors: Set<Int>,
    val unreadableBlocks: Set<Int>,
    val access: Map<Int, SectorAccess>,
) {
    /** The blocks a clone may copy: readable data blocks only (no manufacturer block, never a trailer). */
    val dataBlocks: Map<Int, Bytes>
        get() = blocks.filterKeys { !layout.isManufacturerBlock(it) && !layout.isTrailer(it) }

    fun toMemoryDump(): MemoryDump {
        val missing = (0 until layout.blockCount).filter { it !in blocks }.toSet()
        val pages = (0 until layout.blockCount).map { blocks[it] ?: Bytes.copyOf(ByteArray(ClassicLayout.BLOCK_SIZE)) }
        val annotations = (0 until layout.blockCount).map { block ->
            val region = when {
                layout.isManufacturerBlock(block) -> Region.UID
                layout.isTrailer(block) -> Region.SECTOR_TRAILER
                else -> Region.USER
            }
            PageAnnotation(block, listOf(region))
        }
        return MemoryDump(null, pages, null, null, annotations, unreadableBlocks = missing)
    }
}

/** Reads a card sector by sector with public keys only; never writes. */
object ClassicReader {

    /** Only a vanished tag (or cancellation) is an error: a locked sector or a refused block is data, not a failure. */
    suspend fun read(classic: SafeClassic, progress: (sector: Int) -> Unit = {}): TagResult<ClassicImage> {
        val layout = classic.layout
        val opener = SectorOpener(classic)
        val blocks = HashMap<Int, Bytes>()
        val locked = LinkedHashSet<Int>()
        val refused = LinkedHashSet<Int>()
        val access = HashMap<Int, SectorAccess>()

        for (sector in 0 until layout.sectorCount) {
            progress(sector)
            val opened = when (val result = opener.open(sector)) {
                is Outcome.Err -> if (isFatal(result.error)) return result else null
                is Outcome.Ok -> result.value
            }
            if (opened == null) {
                locked += sector
                continue
            }
            access[sector] = opened
            val first = layout.firstBlockOf(sector)
            for (block in first until first + layout.blocksIn(sector)) {
                when (val read = classic.readBlock(block)) {
                    is Outcome.Ok -> blocks[block] = read.value
                    is Outcome.Err -> {
                        if (isFatal(read.error)) return read
                        refused += block
                        // A refused read ends the authenticated session: open the sector again for the rest of it.
                        if (opener.open(sector) !is Outcome.Ok) return Outcome.Err(TagError.TagLost)
                    }
                }
            }
        }
        return Outcome.Ok(ClassicImage(layout, blocks, locked, refused, access))
    }

    private fun isFatal(error: TagError) = error == TagError.TagLost || error == TagError.Cancelled || error == TagError.Timeout
}
