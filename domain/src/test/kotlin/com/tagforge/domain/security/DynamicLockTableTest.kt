package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ntag.NtagModel
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dynamic lock bytes, checked against NXP NTAG213/215/216 datasheet Rev. 3.2 (doc 265332), section 8.5.3:
 *  - Fig 10 (NTAG213, page 28h): byte 0 bit k locks pages 16+2k..17+2k; byte 1 bits 0-3 lock 32-33, 34-35, 36-37, 38-39;
 *    byte 1 bits 7-4 RFUI; byte 2 bits 0-5 are BL 16-19, 20-23, 24-27, 28-31, 32-35, 36-39; byte 2 bits 7-6 RFUI.
 *  - Fig 11 (NTAG215, page 82h): byte 0 bit k locks pages 16+16k..31+16k (bit 7: 128-129); byte 1 is all RFUI;
 *    byte 2 bits 0-3 are BL 16-47, 48-79, 80-111, 112-129; byte 2 bits 7-4 RFUI.
 *  - Fig 12 (NTAG216, page E2h): byte 0 bit k locks pages 16+16k..31+16k (bit 7: 128-143); byte 1 bits 0-5 lock 144-159,
 *    160-175, 176-191, 192-207, 208-223, 224-225; byte 1 bits 7-6 RFUI; byte 2 bits 0-6 are BL 16-47, 48-79, 80-111,
 *    112-143, 144-175, 176-207, 208-225; byte 2 bit 7 RFUI.
 *  - Byte 3 reads BDh; "set all bits marked with RFUI to 0 when writing".
 */
class DynamicLockTableTest {

    private val chips = NtagModel.entries

    // ---- the tables themselves (figures 10-12) ---------------------------------------------------------------------

    @Test
    fun ntag213LocksTwoPagesPerBit() {
        val layout = DynamicLockTable.layout(NtagModel.NTAG213)

        assertEquals(12, layout.lockGroups.size)
        assertEquals(16..17, layout.lockGroups[0].pages)
        assertEquals(30..31, layout.lockGroups[7].pages)
        assertEquals(32..33, layout.lockGroups[8].pages)
        assertEquals(38..39, layout.lockGroups[11].pages)
        assertEquals(6, layout.blockGroups.size)
        assertEquals(16..19, layout.blockGroups[0].pages)
        assertEquals(36..39, layout.blockGroups[5].pages)
        assertEquals(0x0FFF, layout.vectorMask)
        assertEquals(0x3F, layout.blockMask)
    }

    @Test
    fun ntag215LocksSixteenPagesPerBitAndTheLastGroupIsShort() {
        val layout = DynamicLockTable.layout(NtagModel.NTAG215)

        assertEquals(8, layout.lockGroups.size)
        assertEquals(16..31, layout.lockGroups[0].pages)
        assertEquals(112..127, layout.lockGroups[6].pages)
        assertEquals(128..129, layout.lockGroups[7].pages)
        assertEquals(listOf(16..47, 48..79, 80..111, 112..129), layout.blockGroups.map { it.pages })
        assertEquals(0x00FF, layout.vectorMask)
        assertEquals(0x0F, layout.blockMask)
    }

    @Test
    fun ntag216UsesTheSecondByteForTheTopGroups() {
        val layout = DynamicLockTable.layout(NtagModel.NTAG216)

        assertEquals(14, layout.lockGroups.size)
        assertEquals(128..143, layout.lockGroups[7].pages)
        assertEquals(144..159, layout.lockGroups[8].pages)
        assertEquals(224..225, layout.lockGroups[13].pages)
        assertEquals(listOf(16..47, 48..79, 80..111, 112..143, 144..175, 176..207, 208..225), layout.blockGroups.map { it.pages })
        assertEquals(0x3FFF, layout.vectorMask)
        assertEquals(0x7F, layout.blockMask)
    }

    @Test
    fun everyUserPageFromSixteenOnBelongsToExactlyOneGroup() {
        for (model in chips) {
            val layout = DynamicLockTable.layout(model)
            for (page in 16..model.userEnd) {
                assertEquals(1, layout.lockGroups.count { page in it.pages }, "${model.displayName} page $page")
            }
            assertNull(layout.groupOf(15), "page 15 is the static lock area")
            assertNull(layout.groupOf(model.userEnd + 1), "${model.displayName}: the dynamic lock page itself is not covered")
        }
        assertEquals(0, DynamicLockTable.layout(NtagModel.NTAG213).groupOf(17)!!.index)
        assertEquals(1, DynamicLockTable.layout(NtagModel.NTAG213).groupOf(18)!!.index)
        assertEquals(7, DynamicLockTable.layout(NtagModel.NTAG215).groupOf(129)!!.index)
        assertEquals(13, DynamicLockTable.layout(NtagModel.NTAG216).groupOf(225)!!.index)
    }

    // ---- exact commands ----------------------------------------------------------------------------------------------

    @Test
    fun lockingEveryGroupYieldsTheSpecifiedFrames() {
        assertEquals("A2 28 FF 0F 00 00".hex(), DynamicLockTable.layout(NtagModel.NTAG213).commandForAll())
        assertEquals("A2 82 FF 00 00 00".hex(), DynamicLockTable.layout(NtagModel.NTAG215).commandForAll())
        assertEquals("A2 E2 FF 3F 00 00".hex(), DynamicLockTable.layout(NtagModel.NTAG216).commandForAll())
    }

    @Test
    fun singleGroupsAndBlockBitsLandOnTheirOwnBit() {
        val ntag215 = DynamicLockTable.layout(NtagModel.NTAG215)
        assertEquals("A2 82 01 00 00 00".hex(), ntag215.command(lockGroups = setOf(0)))
        assertEquals("A2 82 80 00 00 00".hex(), ntag215.command(lockGroups = setOf(7)))
        assertEquals("A2 82 00 00 08 00".hex(), ntag215.command(blockGroups = setOf(3)))

        val ntag216 = DynamicLockTable.layout(NtagModel.NTAG216)
        assertEquals("A2 E2 00 01 00 00".hex(), ntag216.command(lockGroups = setOf(8)))
        assertEquals("A2 E2 00 20 00 00".hex(), ntag216.command(lockGroups = setOf(13)))
        assertEquals("A2 E2 00 00 40 00".hex(), ntag216.command(blockGroups = setOf(6)))
    }

    @Test
    fun aRangeOfPagesIsCoveredByEveryGroupItTouches() {
        val ntag213 = DynamicLockTable.layout(NtagModel.NTAG213)

        assertEquals(setOf(0, 1), ntag213.groupsCovering(16..19).map { it.index }.toSet())
        assertEquals(setOf(0, 1), ntag213.groupsCovering(17..18).map { it.index }.toSet(), "partly covered groups lock whole")
        assertTrue(ntag213.groupsCovering(4..15).isEmpty(), "pages below 16 are static lock territory")
    }

    @Test
    fun groupsThatDoNotExistOnTheChipAreRejected() {
        assertFailsWith<IllegalArgumentException> { DynamicLockTable.layout(NtagModel.NTAG215).command(lockGroups = setOf(8)) }
        assertFailsWith<IllegalArgumentException> { DynamicLockTable.layout(NtagModel.NTAG213).command(blockGroups = setOf(6)) }
        assertFailsWith<IllegalArgumentException> { DynamicLockTable.layout(NtagModel.NTAG216).command(lockGroups = setOf(-1)) }
    }

    // ---- NS-06 "RFUI never set" (property) ---------------------------------------------------------------------------

    @Test
    fun noGeneratedCommandEverSetsAnRfuiBitOrByteThree() {
        val random = Random(2015)
        for (model in chips) {
            val layout = DynamicLockTable.layout(model)
            val allowedBytes = listOf(layout.vectorMask and 0xFF, (layout.vectorMask shr 8) and 0xFF, layout.blockMask)
            repeat(3000) {
                val locks = layout.lockGroups.filter { random.nextBoolean() }.map { it.index }.toSet()
                val blocks = layout.blockGroups.filter { random.nextInt(4) == 0 }.map { it.index }.toSet()
                val frame = layout.command(locks, blocks)

                assertEquals(6, frame.size)
                assertEquals(0xA2, frame[0])
                assertEquals(model.dynLock, frame[1])
                for (i in 0..2) assertEquals(0, frame[2 + i] and allowedBytes[i].inv(), "${model.displayName}: RFUI bit set in byte $i of $frame")
                assertEquals(0, frame[5], "byte 3 of the dynamic lock page is written as 00")
            }
        }
    }

    // ---- the table agrees with the fake, which implements the datasheet independently ------------------------------

    @Test
    fun lockingEverythingLocksExactlyTheUserPagesFromSixteenOn() = runTest {
        for (model in chips) {
            val tag = FakeNtag21x(model)
            tag.send(DynamicLockTable.layout(model).commandForAll())

            for (page in 4..model.userEnd) {
                assertEquals(page >= 16, tag.isPageLocked(page), "${model.displayName} page $page")
            }
            assertEquals(0xBD, tag.page(model.dynLock)[3], "byte 3 still reads BDh")
        }
    }

    @Test
    fun eachSingleGroupLocksExactlyItsOwnPages() = runTest {
        for (model in chips) {
            val layout = DynamicLockTable.layout(model)
            for (group in layout.lockGroups) {
                val tag = FakeNtag21x(model)
                tag.send(layout.command(lockGroups = setOf(group.index)))

                val locked = (16..model.userEnd).filter(tag::isPageLocked)
                assertEquals(group.pages.toList(), locked, "${model.displayName} group ${group.index}")
            }
        }
    }

    private suspend fun FakeNtag21x.send(command: Bytes) {
        assertEquals(Outcome.Ok("0A".hex()), transceive(command))
    }
}
