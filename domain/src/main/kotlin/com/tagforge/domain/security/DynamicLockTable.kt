package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel

/**
 * Dynamic lock bytes of NTAG213/215/216 (datasheet Rev. 3.2, section 8.5.3, figures 10-12).
 *
 * A lock group is one bit that makes [pages] read-only for good (bits are OR-ed and never return to 0). A block group is one
 * bit of byte 2 that freezes the lock bits of the groups it covers. Every bit marked RFUI stays 0 and byte 3 is written 00.
 */
object DynamicLockTable {

    /** [index] counts from 0: group k is bit k of the 16-bit vector (byte 0 low, byte 1 high). */
    data class LockGroup(val index: Int, val pages: IntRange)

    /** [index] is the bit number in byte 2; [pages] is the memory area whose lock bits it freezes. */
    data class BlockGroup(val index: Int, val pages: IntRange)

    class Layout internal constructor(
        val model: NtagModel,
        val lockGroups: List<LockGroup>,
        val blockGroups: List<BlockGroup>,
    ) {
        /** Bits of bytes 0-1 that the chip implements; everything else is RFUI. */
        val vectorMask: Int = lockGroups.fold(0) { acc, g -> acc or (1 shl g.index) }

        /** Bits of byte 2 that the chip implements; everything else is RFUI. */
        val blockMask: Int = blockGroups.fold(0) { acc, g -> acc or (1 shl g.index) }

        fun groupOf(page: Int): LockGroup? = lockGroups.firstOrNull { page in it.pages }

        /** Every group that contains at least one of [pages]: a partly covered group locks as a whole. */
        fun groupsCovering(pages: IntRange): List<LockGroup> = lockGroups.filter { g -> pages.first <= g.pages.last && g.pages.first <= pages.last }

        /** `A2 <dynamic lock page> b0 b1 b2 00` for the chosen groups. */
        fun command(lockGroups: Set<Int> = emptySet(), blockGroups: Set<Int> = emptySet()): Bytes {
            val vector = bits(lockGroups, vectorMask, "lock group")
            val blocks = bits(blockGroups, blockMask, "block group")
            val payload = Bytes.of(vector and 0xFF, (vector shr 8) and 0xFF, blocks, 0x00)
            return NtagCommands.write(model.dynLock, payload)
        }

        /** Locks every group (no block bits, so the lock bits themselves are not frozen). */
        fun commandForAll(): Bytes = command(lockGroups = lockGroups.map { it.index }.toSet())

        private fun bits(indexes: Set<Int>, mask: Int, what: String): Int {
            var value = 0
            for (i in indexes) {
                require(i in 0..MAX_BIT && (1 shl i) and mask != 0) { "${model.displayName} has no $what $i" }
                value = value or (1 shl i)
            }
            return value
        }
    }

    fun layout(model: NtagModel): Layout = when (model) {
        NtagModel.NTAG213 -> Layout(model, twoPageGroups(), blocksOfFourPages())
        NtagModel.NTAG215 -> Layout(model, sixteenPageGroups(model.userEnd), unevenBlocks(listOf(16..47, 48..79, 80..111, 112..129)))
        NtagModel.NTAG216 -> Layout(
            model,
            sixteenPageGroups(model.userEnd),
            unevenBlocks(listOf(16..47, 48..79, 80..111, 112..143, 144..175, 176..207, 208..225)),
        )
    }

    private const val MAX_BIT = 15
    private const val FIRST_DYNAMIC_PAGE = 16

    /** Figure 10: 12 groups of 2 pages, pages 16-39. */
    private fun twoPageGroups() = (0 until 12).map { LockGroup(it, (FIRST_DYNAMIC_PAGE + 2 * it)..(FIRST_DYNAMIC_PAGE + 2 * it + 1)) }

    /** Figure 10: block bits 0-5 cover 4 pages each, 16-39. */
    private fun blocksOfFourPages() = (0 until 6).map { BlockGroup(it, (FIRST_DYNAMIC_PAGE + 4 * it)..(FIRST_DYNAMIC_PAGE + 4 * it + 3)) }

    /** Figures 11 and 12: groups of 16 pages from page 16; the last one stops at the last user page. */
    private fun sixteenPageGroups(userEnd: Int): List<LockGroup> {
        val groups = ArrayList<LockGroup>()
        var first = FIRST_DYNAMIC_PAGE
        while (first <= userEnd) {
            groups += LockGroup(groups.size, first..minOf(first + 15, userEnd))
            first += 16
        }
        return groups
    }

    private fun unevenBlocks(areas: List<IntRange>): List<BlockGroup> = areas.mapIndexed { i, pages -> BlockGroup(i, pages) }
}
