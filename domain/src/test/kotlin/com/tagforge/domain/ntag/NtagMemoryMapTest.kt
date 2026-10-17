package com.tagforge.domain.ntag

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TT-03: the role of every page of NTAG213/215/216 (datasheet Rev. 3.2 sections 8.5 to 8.8). */
class NtagMemoryMapTest {

    private fun roles(model: NtagModel, page: Int) = NtagMemoryMap.regionsOf(model, page)

    @Test
    fun ntag213PageByPage() {
        val model = NtagModel.NTAG213
        assertEquals(listOf(Region.UID), roles(model, 0))
        assertEquals(listOf(Region.UID), roles(model, 1))
        assertEquals(listOf(Region.UID, Region.STATIC_LOCK), roles(model, 2), "page 2 holds BCC1 (bytes 0-1) and the static lock bytes (2-3)")
        assertEquals(listOf(Region.CC), roles(model, 3))
        assertEquals(listOf(Region.USER), roles(model, 4))
        assertEquals(listOf(Region.USER), roles(model, 39))
        assertEquals(listOf(Region.DYN_LOCK), roles(model, 40))
        assertEquals(listOf(Region.CFG0), roles(model, 41))
        assertEquals(listOf(Region.CFG1), roles(model, 42))
        assertEquals(listOf(Region.PWD), roles(model, 43))
        assertEquals(listOf(Region.PACK), roles(model, 44))
    }

    @Test
    fun ntag215PageByPage() {
        val model = NtagModel.NTAG215
        assertEquals(listOf(Region.USER), roles(model, 4))
        assertEquals(listOf(Region.USER), roles(model, 129))
        assertEquals(listOf(Region.DYN_LOCK), roles(model, 130))
        assertEquals(listOf(Region.CFG0), roles(model, 131))
        assertEquals(listOf(Region.CFG1), roles(model, 132))
        assertEquals(listOf(Region.PWD), roles(model, 133))
        assertEquals(listOf(Region.PACK), roles(model, 134))
    }

    @Test
    fun ntag216AnnotationsMatchTheSpecScenario() {
        val model = NtagModel.NTAG216
        assertEquals(listOf(Region.USER), roles(model, 225))
        assertEquals(listOf(Region.DYN_LOCK), roles(model, 226))
        assertEquals(listOf(Region.CFG0), roles(model, 227), "CFG0 (AUTH0 = byte 3)")
        assertEquals(listOf(Region.CFG1), roles(model, 228), "CFG1 (ACCESS)")
        assertEquals(listOf(Region.PWD), roles(model, 229))
        assertEquals(listOf(Region.PACK), roles(model, 230))
    }

    @Test
    fun pagesOutsideTheChipHaveNoRole() {
        assertEquals(emptyList(), roles(NtagModel.NTAG213, 45))
        assertEquals(emptyList(), roles(NtagModel.NTAG216, 231))
        assertEquals(emptyList(), roles(NtagModel.NTAG215, -1))
    }

    @Test
    fun everyPageOfEveryChipHasARoleAndUserMemoryIsOneContiguousRun() {
        for (model in NtagModel.entries) {
            val userPages = (0 until model.totalPages).filter { Region.USER in roles(model, it) }
            assertEquals((model.userStart..model.userEnd).toList(), userPages, "$model user memory")
            for (page in 0 until model.totalPages) {
                assertTrue(roles(model, page).isNotEmpty(), "$model page $page has a role")
            }
        }
    }

    @Test
    fun onlyPwdAndPackAreHiddenByTheChip() {
        assertEquals(setOf(Region.PWD, Region.PACK), Region.entries.filter { it.hiddenByChip }.toSet())
        assertFalse(Region.USER.hiddenByChip)
    }

    @Test
    fun annotateCoversTheGivenNumberOfPagesAndIsEmptyForOtherChips() {
        val partial = NtagMemoryMap.annotate(NtagModel.NTAG213, pageCount = 6)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), partial.map { it.page })
        assertEquals(listOf(Region.UID, Region.UID, Region.UID, Region.CC, Region.USER, Region.USER), partial.map { it.region })

        assertEquals(45, NtagMemoryMap.annotate(NtagModel.NTAG213, pageCount = 45).size)
        assertEquals(emptyList(), NtagMemoryMap.annotate(null, pageCount = 16), "non-NTAG tags show raw pages without annotations")
    }

    @Test
    fun aMemoryDumpExposesItsAnnotations() {
        val pages = (0 until 4).map { Bytes.of(it, it, it, it) }
        val dump = MemoryDump(NtagModel.NTAG215, pages, unreadable = 4..134)

        assertEquals(listOf(Region.UID, Region.UID, Region.UID, Region.CC), dump.annotations.map { it.region })
        assertEquals(emptyList(), MemoryDump(null, pages, unreadable = null).annotations)
    }

    @Test
    fun pwdAndPackAreDisplayedAsZerosWithAHiddenByChipNote() {
        val dump = MemoryDump(NtagModel.NTAG213, (0 until 45).map { "04 05 06 07".hex() }, unreadable = null)

        assertEquals("00 00 00 00".hex(), dump.displayBytes(43), "whatever the pages hold, the chip hides the password")
        assertEquals("00 00 00 00".hex(), dump.displayBytes(44))
        assertEquals("04 05 06 07".hex(), dump.displayBytes(42))
        assertTrue(dump.isHiddenByChip(43))
        assertFalse(dump.isHiddenByChip(42))
    }
}
