package com.tagforge.domain.classic

import com.tagforge.domain.common.Bytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassicLayoutTest {

    @Test
    fun sizesMatchMiniOneKTwoKAndFourK() {
        assertEquals(20, ClassicLayout.MINI.blockCount)
        assertEquals(64, ClassicLayout.CLASSIC_1K.blockCount)
        assertEquals(1024, ClassicLayout.CLASSIC_1K.sizeBytes)
        assertEquals(128, ClassicLayout.CLASSIC_2K.blockCount)
        assertEquals(256, ClassicLayout.CLASSIC_4K.blockCount)
        assertEquals(4096, ClassicLayout.CLASSIC_4K.sizeBytes)
    }

    @Test
    fun theTrailerIsTheLastBlockOfEverySector() {
        val k1 = ClassicLayout.CLASSIC_1K
        assertEquals((0 until 16).map { it * 4 + 3 }, (0 until 16).map(k1::trailerOf))
        assertEquals(63, k1.trailerOf(15))
        assertEquals(19, ClassicLayout.MINI.trailerOf(4))
    }

    @Test
    fun the4KLargeSectorsHaveSixteenBlocks() {
        val k4 = ClassicLayout.CLASSIC_4K
        assertEquals(128, k4.firstBlockOf(32))
        assertEquals(16, k4.blocksIn(32))
        assertEquals(143, k4.trailerOf(32))
        assertEquals(255, k4.trailerOf(39))
        assertEquals(32, k4.sectorOf(128))
        assertEquals(39, k4.sectorOf(255))
        assertEquals(31, k4.sectorOf(127))
    }

    @Test
    fun everyTrailerOfEveryLayoutIsRecognisedAndNothingElseIs() {
        listOf(ClassicLayout.MINI, ClassicLayout.CLASSIC_1K, ClassicLayout.CLASSIC_2K, ClassicLayout.CLASSIC_4K).forEach { layout ->
            val trailers = (0 until layout.sectorCount).map(layout::trailerOf).toSet()
            assertEquals(trailers, (0 until layout.blockCount).filter(layout::isTrailer).toSet(), "$layout")
        }
    }

    @Test
    fun writableBlocksExcludeBlockZeroAndTheTrailer() {
        val k1 = ClassicLayout.CLASSIC_1K
        assertEquals(1..2, k1.writableBlocksOf(0))
        assertEquals(4..6, k1.writableBlocksOf(1))
        assertEquals(128..142, ClassicLayout.CLASSIC_4K.writableBlocksOf(32))
        assertTrue((0 until k1.sectorCount).none { s -> k1.trailerOf(s) in k1.writableBlocksOf(s) })
    }

    @Test
    fun onlyRealSectorCountsHaveALayout() {
        assertEquals(ClassicLayout.CLASSIC_4K, ClassicLayout.ofSectors(40))
        assertNull(ClassicLayout.ofSectors(7))
        assertFailsWith<IllegalArgumentException> { ClassicLayout.CLASSIC_1K.trailerOf(16) }
    }

    @Test
    fun onlyThreePublicKeysExist() {
        assertEquals(
            listOf(Bytes.of(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF), Bytes.of(0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5), Bytes.of(0xD3, 0xF7, 0xD3, 0xF7, 0xD3, 0xF7)),
            ClassicKeys.ALL,
        )
        assertFalse(ClassicKeys.ALL.any { it == Bytes.of(0, 0, 0, 0, 0, 0) })
    }
}
