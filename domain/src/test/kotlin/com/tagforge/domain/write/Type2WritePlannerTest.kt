package com.tagforge.domain.write

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.Type2Tlv
import com.tagforge.domain.ntag.NtagModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The page maths behind every NTAG21x write: 1-byte vs 3-byte TLV length, exact-fit messages that drop the terminator,
 * and the interrupt-safe order (length cleared first, written last). Pure planner plus an end-to-end run on [FakeNtag21x].
 */
class Type2WritePlannerTest {

    /** Non-zero, non-0xFE filler so no page of the body ever equals a blank page and gets skipped. */
    private fun message(size: Int): Bytes = Bytes.of(*IntArray(size) { (it % 200) + 1 })

    private fun userArea(tag: FakeNtag21x): Bytes = Bytes.concat((tag.model.userStart..tag.model.userEnd).map { tag.page(it) })

    private fun plan(tag: FakeNtag21x, message: Bytes, leading: Bytes = Bytes.EMPTY): List<PageWrite>? =
        Type2WritePlanner.plan(tag.model, userArea(tag), message, leading)

    private suspend fun apply(tag: FakeNtag21x, steps: List<PageWrite>) {
        assertTrue(Type2Io.execute(tag, steps) is Outcome.Ok, "the planned writes were refused by the tag")
    }

    private fun readBack(tag: FakeNtag21x): Bytes? = (Type2Tlv.unwrap(userArea(tag)) as? Outcome.Ok)?.value?.message

    // ---- 3-byte TLV length (message > 254 B) ------------------------------------------------------------

    @Test
    fun aMessageOver254BytesUsesTheThreeByteLengthForm() {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        val steps = plan(tag, message(300))!!

        // 300 = 0x012C: `03 FF 01 2C`; the marker byte is zeroed while the tag is being written
        assertEquals(PageWrite(4, "03 00 01 2C".hex()), steps.first(), "header first, with the length cleared")
        assertEquals(PageWrite(4, "03 FF 01 2C".hex()), steps.last(), "real 3-byte length committed last")
    }

    @Test
    fun theBodyOfALongMessageIsWrittenBetweenTheClearedAndTheFinalHeader() {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        val body = message(300)
        val steps = plan(tag, body)!!

        // framed = 4 header + 300 + terminator = 305 bytes = 77 pages: header, 76 body pages, header again
        assertEquals(78, steps.size)
        assertEquals((5..80).toList(), steps.subList(1, steps.size - 1).map { it.page }, "body pages ascend between the two header writes")
        assertEquals(body, Bytes.concat(steps.subList(1, steps.size - 1).map { it.data }).slice(0, 300))
    }

    @Test
    fun theLongLengthIsWrittenLastOnARealTagAndTheMessageReadsBack() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        val body = message(300)

        apply(tag, plan(tag, body)!!)

        assertEquals(4, tag.writtenPages.first())
        assertEquals(4, tag.writtenPages.last())
        assertEquals(2, tag.writtenPages.count { it == 4 }, "page 4 is written exactly twice: cleared, then committed")
        assertEquals(body, readBack(tag))
        assertEquals("03 FF 01 2C".hex(), tag.page(4))
    }

    @Test
    fun theShortFormEndsAt254AndTheLongFormStartsAt255() {
        val short = plan(FakeNtag21x(NtagModel.NTAG215), message(254))!!
        assertEquals("03 00".hex(), short.first().data.slice(0, 2))
        assertEquals("03 FE".hex(), short.last().data.slice(0, 2))

        val long = plan(FakeNtag21x(NtagModel.NTAG215), message(255))!!
        assertEquals("03 FF 00 FF".hex(), long.last().data)
        assertEquals("03 00 00 FF".hex(), long.first().data)
    }

    @Test
    fun aLongHeaderThatCrossesAPageBoundaryIsClearedFirstAndTheMarkerCommittedLast() {
        // Lock Control TLV in front (as on NTAG213) pushes the 4-byte header `03 FF hh ll` over pages 1 and 2
        val tag = FakeNtag21x(NtagModel.NTAG216)
        val lockControl = NtagModel.NTAG213.factoryLockControlTlv!!
        val steps = plan(tag, message(300), lockControl)!!

        val userStart = tag.model.userStart
        assertEquals(listOf(userStart + 1, userStart + 2), steps.take(2).map { it.page }, "both header pages are cleared before any body page")
        assertEquals("34 03 00 01".hex(), steps[0].data, "length marker zeroed, Lock Control TLV tail untouched")
        // only the page holding the 0xFF marker differs between the cleared and the real header, so only it is committed
        assertEquals(userStart + 1, steps.last().page, "the marker page is committed last")
        assertEquals("34 03 FF 01".hex(), steps.last().data)
        assertEquals(1, steps.count { it.page == userStart + 1 && it.data == "34 03 FF 01".hex() }, "committed exactly once")
    }

    // ---- exact fit ---------------------------------------------------------------------------------------

    @Test
    fun anExactFitNtag215MessageOf492BytesIsPlannedWithoutTheTerminator() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        val body = message(492)

        val steps = plan(tag, body)
        assertNotNull(steps)
        // 4-byte header + 492 = 496 = the whole CC NDEF area (124 pages); the 0xFE terminator does not fit and is dropped
        assertEquals(124, steps.map { it.page }.toSet().size)
        assertEquals(body[491], steps.first { it.page == tag.model.userStart + 123 }.data[3], "the last byte of the area is message data, not 0xFE")

        apply(tag, steps)
        assertEquals(body, readBack(tag))
        assertEquals("03 FF 01 EC".hex(), tag.page(4))   // 492 = 0x01EC
    }

    @Test
    fun oneByteOverTheNtag215CapacityIsRefusedBeforeAnyWrite() {
        assertNull(plan(FakeNtag21x(NtagModel.NTAG215), message(493)))
    }

    @Test
    fun anExactFitNtag216MessageOf868BytesIsPlannedAndReadsBack() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG216)
        val body = message(868)

        val steps = plan(tag, body)
        assertNotNull(steps)
        assertEquals(218, steps.map { it.page }.toSet().size)   // 4 + 868 = 872 bytes = 218 pages

        apply(tag, steps)
        assertEquals(body, readBack(tag))
        assertEquals("03 FF 03 64".hex(), tag.page(4))   // 868 = 0x0364
        assertEquals(4, tag.writtenPages.first())
        assertEquals(4, tag.writtenPages.last())
    }

    @Test
    fun oneByteOverTheNtag216CapacityIsRefused() {
        assertNull(plan(FakeNtag21x(NtagModel.NTAG216), message(869)))
    }

    @Test
    fun theTerminatorIsKeptWhenThereIsRoomForIt() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        apply(tag, plan(tag, message(491))!!)   // 4 + 491 + FE = 496: fits exactly with the terminator
        assertEquals(0xFE, tag.page(4 + 123)[3])
        assertEquals(message(491), readBack(tag))
    }
}
