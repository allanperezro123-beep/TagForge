package com.tagforge.domain.fakes

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The fake is test infrastructure for every later phase, so its datasheet behaviour is pinned here. */
class FakeNtag21xTest {

    private suspend fun FakeNtag21x.send(command: Bytes): Bytes {
        val result = transceive(command)
        check(result is Outcome.Ok) { "transceive failed: $result" }
        return result.value
    }

    private val nak0 = Bytes.of(0x00)
    private val ack = Bytes.of(0x0A)

    // ---- blank memory -------------------------------------------------------------------------

    @Test
    fun blankNtag213MatchesFactoryContent() {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        assertEquals(45, tag.pageCount)
        assertEquals("E1 10 12 00".hex(), tag.page(3))
        assertEquals("01 03 A0 0C".hex(), tag.page(4))
        assertEquals("34 03 00 FE".hex(), tag.page(5))
        assertEquals("00 00 00 FF".hex(), tag.page(NtagModel.NTAG213.cfg0))
    }

    @Test
    fun blankNtag215And216StartWithEmptyNdefTlv() {
        assertEquals("03 00 FE 00".hex(), FakeNtag21x(NtagModel.NTAG215).page(4))
        assertEquals("E1 10 3E 00".hex(), FakeNtag21x(NtagModel.NTAG215).page(3))
        assertEquals(135, FakeNtag21x(NtagModel.NTAG215).pageCount)
        assertEquals("03 00 FE 00".hex(), FakeNtag21x(NtagModel.NTAG216).page(4))
        assertEquals("E1 10 6D 00".hex(), FakeNtag21x(NtagModel.NTAG216).page(3))
        assertEquals(231, FakeNtag21x(NtagModel.NTAG216).pageCount)
    }

    @Test
    fun uidAndBccAreConsistentInPageZeroToTwo() {
        val tag = FakeNtag21x(NtagModel.NTAG215, uid = "04 A2 3B 1C 5D 6E 80".hex())
        assertEquals("04 A2 3B".hex() + Bytes.of(0x88 xor 0x04 xor 0xA2 xor 0x3B), tag.page(0))
        assertEquals("1C 5D 6E 80".hex(), tag.page(1))
        assertEquals(0x1C xor 0x5D xor 0x6E xor 0x80, tag.page(2)[0])
    }

    // ---- commands -----------------------------------------------------------------------------

    @Test
    fun getVersionReturnsTheModelBytes() = runTest {
        assertEquals("00 04 04 02 01 00 0F 03".hex(), FakeNtag21x(NtagModel.NTAG213).send(NtagCommands.getVersion()))
        assertEquals("00 04 04 02 01 00 11 03".hex(), FakeNtag21x(NtagModel.NTAG215).send(NtagCommands.getVersion()))
        assertEquals("00 04 04 02 01 00 13 03".hex(), FakeNtag21x(NtagModel.NTAG216).send(NtagCommands.getVersion()))
    }

    @Test
    fun readReturnsFourPages() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        assertEquals(tag.page(4) + tag.page(5) + tag.page(6) + tag.page(7), tag.send(NtagCommands.read(4)))
    }

    @Test
    fun readRollsOverToPageZeroPastTheEnd() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)   // last page 44 (0x2C)
        val response = tag.send(NtagCommands.read(43))
        assertEquals(tag.page(43) + tag.page(44) + tag.page(0) + tag.page(1), response)
    }

    @Test
    fun readBeyondTheLastPageIsNak() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        assertEquals(nak0, tag.send(NtagCommands.read(45)))
        assertEquals(16, tag.send(NtagCommands.read(44)).size)
    }

    @Test
    fun fastReadReturnsTheRequestedRangeAndRejectsBadRanges() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        assertEquals(tag.page(4) + tag.page(5) + tag.page(6), tag.send(NtagCommands.fastRead(4, 6)))
        assertEquals(45 * 4, tag.send(NtagCommands.fastRead(0, 44)).size)
        assertEquals(nak0, tag.send(NtagCommands.fastRead(40, 45)))
        assertEquals(nak0, tag.send("3A 08 04".hex()))   // start after end, raw frame
    }

    @Test
    fun writeStoresDataAcksAndCounts() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        assertEquals(ack, tag.send(NtagCommands.write(4, "D1 01 05 54".hex())))
        assertEquals("D1 01 05 54".hex(), tag.page(4))
        assertEquals(1, tag.writeCount)
        assertEquals(listOf(4), tag.writtenPages)

        tag.send(NtagCommands.write(5, "02 65 6E 68".hex()))
        assertEquals(listOf(4, 5), tag.writtenPages)
        assertEquals("D1 01 05 54".hex() + "02 65 6E 68".hex(), tag.send(NtagCommands.fastRead(4, 5)))
    }

    @Test
    fun pagesZeroAndOneAreNeverWritableAndOutOfRangeIsNak() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        val before0 = tag.page(0)
        assertEquals(nak0, tag.send(NtagCommands.write(0, "AA BB CC DD".hex())))
        assertEquals(nak0, tag.send(NtagCommands.write(1, "AA BB CC DD".hex())))
        assertEquals(nak0, tag.send(NtagCommands.write(45, "AA BB CC DD".hex())))
        assertEquals(before0, tag.page(0))
        assertEquals(0, tag.writeCount)
    }

    @Test
    fun unknownCommandsAreNak() = runTest {
        assertEquals(nak0, FakeNtag21x(NtagModel.NTAG213).send("EE 01".hex()))
    }

    // ---- password protection gating -----------------------------------------------------------

    private fun protectedTag(prot: Boolean): FakeNtag21x {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        tag.setPage(NtagModel.NTAG215.cfg0, "00 00 00 04".hex())                 // AUTH0 = 04
        tag.setPage(NtagModel.NTAG215.cfg1, Bytes.of(if (prot) 0x80 else 0x00, 0, 0, 0))   // PROT bit
        return tag
    }

    @Test
    fun protReadsAndWritesAtOrAboveAuth0AreNakUntilAuthenticated() = runTest {
        val tag = protectedTag(prot = true)
        assertEquals(nak0, tag.send(NtagCommands.read(4)))
        assertEquals(nak0, tag.send(NtagCommands.fastRead(0, 4)))
        assertEquals(nak0, tag.send(NtagCommands.write(4, "01 02 03 04".hex())))
        assertEquals(16, tag.send(NtagCommands.fastRead(0, 3)).size)   // below AUTH0 stays readable

        tag.authenticated = true
        assertEquals(16, tag.send(NtagCommands.read(4)).size)
        assertEquals(ack, tag.send(NtagCommands.write(4, "01 02 03 04".hex())))
    }

    @Test
    fun readStraddlingAuth0RollsOverToPageZeroInsteadOfLeaking() = runTest {
        val tag = protectedTag(prot = true)
        tag.setPage(4, "DE AD BE EF".hex())
        // READ 2 would cover pages 2,3,4,5; with AUTH0=4 the response is pages 2,3 then rolled-over 0,1
        assertEquals(tag.page(2) + tag.page(3) + tag.page(0) + tag.page(1), tag.send(NtagCommands.read(2)))
    }

    @Test
    fun writeProtectionOnlyLeavesReadsOpen() = runTest {
        val tag = protectedTag(prot = false)
        assertEquals(16, tag.send(NtagCommands.read(4)).size)
        assertEquals(nak0, tag.send(NtagCommands.write(4, "01 02 03 04".hex())))
        assertEquals(ack, tag.send(NtagCommands.write(3, "E1 10 3E 00".hex())))
    }

    // ---- fault injection ----------------------------------------------------------------------

    @Test
    fun loseTagAfterNWritesFailsEverythingAfterwards() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        tag.loseTagAfter(3)
        repeat(3) { i -> assertEquals(ack, tag.send(NtagCommands.write(4 + i, "11 22 33 44".hex()))) }

        assertEquals(Outcome.Err(TagError.TagLost), tag.transceive(NtagCommands.write(7, "11 22 33 44".hex())))
        assertEquals(Outcome.Err(TagError.TagLost), tag.transceive(NtagCommands.read(0)))
        assertEquals(3, tag.writeCount)
        assertEquals(Bytes.of(0, 0, 0, 0), tag.page(7))   // 4th write never landed
    }

    @Test
    fun failNextFailsExactlyOneTransceive() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        tag.failNext(TagError.Io("boom"))
        assertEquals(Outcome.Err(TagError.Io("boom")), tag.transceive(NtagCommands.read(0)))
        assertEquals(16, tag.send(NtagCommands.read(0)).size)
    }
}
