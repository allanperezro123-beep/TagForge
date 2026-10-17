package com.tagforge.domain.fakes

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Lock semantics of the fake, pinned against NTAG213/215/216 datasheet Rev. 3.2 sections 8.5.2 to 8.5.4:
 * lock bytes and CC are OR-ed (bits never return to 0), block-locking bits freeze their group, RFUI bits of the
 * dynamic lock bytes stay 0 and byte 3 always reads BDh.
 */
class FakeNtag21xLockTest {

    private val ack = Bytes.of(0x0A)
    private val nak = Bytes.of(0x00)

    private suspend fun FakeNtag21x.write(page: Int, data: String): Bytes {
        val result = transceive(NtagCommands.write(page, data.hex()))
        check(result is Outcome.Ok) { "transceive failed: $result" }
        return result.value
    }

    // ---- static lock bytes (page 02, bytes 2-3) -------------------------------------------------------------

    @Test
    fun writingPage2OrsTheLockBytesAndLeavesTheCheckBytesAlone() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        val before = tag.page(2)

        assertEquals(ack, tag.write(2, "00 00 00 0F"))
        assertEquals(before.slice(0, 2) + "00 0F".hex(), tag.page(2))

        assertEquals(ack, tag.write(2, "FF FF 00 00"))
        assertEquals(before.slice(0, 2) + "00 0F".hex(), tag.page(2), "bytes 0-1 are not writable and 00 cannot clear a bit")
    }

    @Test
    fun lockBitsAccumulateAndNeverReturnToZero() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        tag.write(2, "00 00 10 00")
        tag.write(2, "00 00 00 01")
        assertEquals("10 01".hex(), tag.page(2).slice(2))

        tag.write(2, "00 00 00 00")
        assertEquals("10 01".hex(), tag.page(2).slice(2))
    }

    @Test
    fun aBlockLockingBitFreezesItsGroup() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        tag.write(2, "00 00 02 00")   // BL-9-4: L4..L9 can no longer be set
        tag.write(2, "00 00 10 01")   // asks for L4 (byte 0) and L8 (byte 1, bit 0): both inside the frozen group

        assertEquals("02 00".hex(), tag.page(2).slice(2), "L4 and L8 stay 0 because BL-9-4 froze them")
        assertFalse(tag.isPageLocked(4))
        assertFalse(tag.isPageLocked(8))

        tag.write(2, "00 00 80 04")   // L7 is frozen too, L10 (byte 1, bit 2) belongs to the other group
        assertEquals("02 04".hex(), tag.page(2).slice(2))
        assertTrue(tag.isPageLocked(10))
    }

    @Test
    fun aLockBitMakesItsPageReadOnlyAndNaksTheWrite() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        tag.write(2, "00 00 10 00")   // L4

        assertTrue(tag.isPageLocked(4))
        assertFalse(tag.isPageLocked(5))
        val writesBefore = tag.writeCount

        assertEquals(nak, tag.write(4, "AA BB CC DD"))
        assertEquals(writesBefore, tag.writeCount, "a refused write is not a write")
        assertEquals("01 03 A0 0C".hex(), tag.page(4))
        assertEquals(ack, tag.write(5, "34 03 00 FE"))
    }

    @Test
    fun theCcLockBitProtectsThePageThatHoldsTheCapabilityContainer() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        tag.write(2, "00 00 08 00")   // L-CC

        assertEquals(nak, tag.write(3, "00 00 00 0F"))
        assertEquals("E1 10 12 00".hex(), tag.page(3))
    }

    // ---- capability container ------------------------------------------------------------------------------

    @Test
    fun writingTheCcOrsTheBytesSoOnlyTheReadOnlyStateCanBeReached() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)

        assertEquals(ack, tag.write(3, "00 00 00 0F"))
        assertEquals("E1 10 12 0F".hex(), tag.page(3), "the datasheet example: read-only state")

        assertEquals(ack, tag.write(3, "00 00 00 00"))
        assertEquals("E1 10 12 0F".hex(), tag.page(3), "bits never return to 0")
    }

    @Test
    fun aTagReportsNdefReadOnlyOnlyWhenTheCcSaysSoLikeAndroidDoes() = runTest {
        val viaCc = FakeNtag21x(NtagModel.NTAG215)
        assertFalse(viaCc.isNdefWriteLocked, "a factory tag is writable")
        viaCc.write(3, "00 00 00 0F")
        assertTrue(viaCc.isNdefWriteLocked)

        // A page locked through the lock bits still looks writable in the CC: the write itself gets refused.
        val viaLockBit = FakeNtag21x(NtagModel.NTAG216)
        viaLockBit.write(2, "00 00 10 00")
        assertFalse(viaLockBit.isNdefWriteLocked)
        assertTrue(viaLockBit.isPageLocked(4))
        assertEquals("E1 10 6D 00".hex(), viaLockBit.page(3), "the CC was not touched")
    }

    // ---- dynamic lock enforcement ----------------------------------------------------------------------------

    @Test
    fun dynamicLockBitsLockTheirGroupOfPagesAtTheChipsGranularity() = runTest {
        val small = FakeNtag21x(NtagModel.NTAG213)
        small.write(NtagModel.NTAG213.dynLock, "01 00 00 00")   // lock pages 16-17 (2-page groups)
        assertTrue(small.isPageLocked(16))
        assertTrue(small.isPageLocked(17))
        assertFalse(small.isPageLocked(18))
        assertEquals(nak, small.write(17, "01 02 03 04"))

        val large = FakeNtag21x(NtagModel.NTAG215)
        large.write(NtagModel.NTAG215.dynLock, "01 00 00 00")   // lock pages 16-31 (16-page groups)
        assertTrue(large.isPageLocked(16))
        assertTrue(large.isPageLocked(31))
        assertFalse(large.isPageLocked(32))

        val last = FakeNtag21x(NtagModel.NTAG216)
        last.write(NtagModel.NTAG216.dynLock, "00 20 00 00")   // byte 1 bit 5 = pages 224-225
        assertTrue(last.isPageLocked(225))
        assertFalse(last.isPageLocked(223))
    }

    @Test
    fun aDynamicBlockLockingBitFreezesTwoGroups() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        tag.write(NtagModel.NTAG215.dynLock, "00 00 01 00")   // BL 16-47: groups 16-31 and 32-47 are frozen
        tag.write(NtagModel.NTAG215.dynLock, "03 00 00 00")   // asks for groups 16-31 and 32-47

        assertEquals("00 00 01 BD".hex(), tag.page(NtagModel.NTAG215.dynLock))
        assertFalse(tag.isPageLocked(16))
    }

    // ---- dynamic lock bytes ---------------------------------------------------------------------------------

    @Test
    fun dynamicLockBytesIgnoreRfuiBitsAndByte3AlwaysReadsBd() = runTest {
        val cases = mapOf(
            NtagModel.NTAG213 to "FF 0F 3F BD",
            NtagModel.NTAG215 to "FF 00 0F BD",
            NtagModel.NTAG216 to "FF 3F 7F BD",
        )
        for ((model, expected) in cases) {
            val tag = FakeNtag21x(model)
            assertEquals("00 00 00 BD".hex(), tag.page(model.dynLock), "$model factory")

            assertEquals(ack, tag.write(model.dynLock, "FF FF FF 00"))
            assertEquals(expected.hex(), tag.page(model.dynLock), "$model keeps RFUI at 0")

            tag.write(model.dynLock, "00 00 00 00")
            assertEquals(expected.hex(), tag.page(model.dynLock), "$model bits never return to 0")
        }
    }

    // ---- what the session tells the use cases ---------------------------------------------------------------------

    @Test
    fun theSessionReportsNotWritableOnceTheCcIsReadOnlyAndRefusesToWriteOrFormat() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        val session = FakeTagSession.ntag(tag)
        assertTrue(session.info.ndef!!.isWritable)

        tag.write(3, "00 00 00 0F")
        val writesAfterLocking = tag.writeCount

        assertFalse(session.info.ndef!!.isWritable)
        assertEquals(
            Outcome.Err(com.tagforge.domain.tag.TagError.ReadOnly),
            session.writeNdef(com.tagforge.domain.ndef.NdefMessage(listOf(com.tagforge.domain.ndef.NdefRecord.text("x", "en")))),
        )
        assertEquals(Outcome.Err(com.tagforge.domain.tag.TagError.ReadOnly), session.formatNdef(null))
        assertEquals(writesAfterLocking, tag.writeCount, "a refused operation writes nothing")
    }

    // ---- other pages stay plain ------------------------------------------------------------------------------

    @Test
    fun userAndConfigPagesStillOverwriteNormally() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        tag.write(6, "11 22 33 44")
        tag.write(6, "00 00 00 00")
        assertEquals("00 00 00 00".hex(), tag.page(6), "user pages are not OR-ed")

        tag.write(NtagModel.NTAG213.cfg0, "04 00 00 FF")
        tag.write(NtagModel.NTAG213.cfg0, "00 00 00 FF")
        assertEquals("00 00 00 FF".hex(), tag.page(NtagModel.NTAG213.cfg0))
    }
}
