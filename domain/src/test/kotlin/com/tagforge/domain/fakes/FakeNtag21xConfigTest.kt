package com.tagforge.domain.fakes

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** CFGLCK latch, READ_SIG, READ_CNT and the command log of the fake (datasheet Rev. 3.2 sections 8.5.7, 10.5, 10.6). */
class FakeNtag21xConfigTest {

    private val model = NtagModel.NTAG215
    private val ack = Bytes.of(0x0A)
    private val nak = Bytes.of(0x00)

    private suspend fun FakeNtag21x.send(command: Bytes): Bytes {
        val result = transceive(command)
        check(result is Outcome.Ok) { "transceive failed: $result" }
        return result.value
    }

    @Test
    fun cfgLckBlocksConfigWritesOnlyAfterAPowerCycle() = runTest {
        val tag = FakeNtag21x(model)
        assertEquals(ack, tag.send(NtagCommands.write(model.cfg1, "40 00 00 00".hex())), "setting CFGLCK itself is allowed")

        assertEquals(ack, tag.send(NtagCommands.write(model.cfg0, "04 00 05 FF".hex())), "still writable until the power cycle")
        tag.powerCycle()

        assertEquals(nak, tag.send(NtagCommands.write(model.cfg0, "04 00 00 04".hex())), "CFG0 is write-locked now")
        assertEquals(nak, tag.send(NtagCommands.write(model.cfg1, "00 00 00 00".hex())), "CFG1 is write-locked now")
        assertEquals("04 00 05 FF".hex(), tag.page(model.cfg0))
        assertEquals("40 00 00 00".hex(), tag.page(model.cfg1))
    }

    @Test
    fun pwdAndPackStayWritableEvenWithCfgLckActive() = runTest {
        val tag = FakeNtag21x(model)
        tag.setPage(model.cfg1, "40 00 00 00".hex())
        tag.powerCycle()

        assertEquals(ack, tag.send(NtagCommands.write(model.pwd, "01 02 03 04".hex())))
        assertEquals(ack, tag.send(NtagCommands.write(model.pack, "05 06 00 00".hex())))
        assertEquals("01 02 03 04".hex(), tag.page(model.pwd))
    }

    @Test
    fun aPowerCycleForgetsTheAuthentication() = runTest {
        val tag = FakeNtag21x(model)
        tag.setPage(model.pwd, "12 34 56 78".hex())
        tag.setPage(model.cfg1, "80 00 00 00".hex())
        tag.setPage(model.cfg0, "04 00 00 04".hex())
        tag.send(NtagCommands.pwdAuth("12 34 56 78".hex()))
        assertEquals(16, tag.send(NtagCommands.read(4)).size)

        tag.powerCycle()

        assertEquals(nak, tag.send(NtagCommands.read(4)))
    }

    @Test
    fun readSigReturnsTheThirtyTwoByteSignature() = runTest {
        val tag = FakeNtag21x(model)

        val signature = tag.send(NtagCommands.readSig())

        assertEquals(32, signature.size)
        assertEquals(tag.signature, signature)
        tag.signature = Bytes.copyOf(ByteArray(32) { 0x5A })
        assertEquals(Bytes.copyOf(ByteArray(32) { 0x5A }), tag.send(NtagCommands.readSig()))
    }

    @Test
    fun readCntReturnsThreeBytesAndHonoursItsPasswordProtection() = runTest {
        val tag = FakeNtag21x(model)
        tag.nfcCounter = "00 00 2A".hex()
        assertEquals("00 00 2A".hex(), tag.send(NtagCommands.readCnt()))

        tag.setPage(model.cfg1, "08 00 00 00".hex())   // NFC_CNT_PWD_PROT
        assertEquals(nak, tag.send(NtagCommands.readCnt()), "counter is password protected")
        tag.authenticated = true
        assertEquals("00 00 2A".hex(), tag.send(NtagCommands.readCnt()))
    }

    @Test
    fun theCommandLogKeepsEveryFrameInOrderAndFiltersWrites() = runTest {
        val tag = FakeNtag21x(model)
        tag.send(NtagCommands.getVersion())
        tag.send(NtagCommands.write(4, "01 02 03 04".hex()))
        tag.send(NtagCommands.read(4))
        tag.send(NtagCommands.write(5, "05 06 07 08".hex()))

        assertEquals(listOf("60", "A2 04 01 02 03 04", "30 04", "A2 05 05 06 07 08").map { it.hex() }, tag.commandLog)
        assertEquals(listOf("A2 04 01 02 03 04", "A2 05 05 06 07 08").map { it.hex() }, tag.writeCommands)
    }

    @Test
    fun aLostTagRecordsNothingMore() = runTest {
        val tag = FakeNtag21x(model)
        tag.loseTagAfter(1)
        tag.send(NtagCommands.write(4, "01 02 03 04".hex()))

        val lost = tag.transceive(NtagCommands.write(5, "05 06 07 08".hex()))

        assertEquals(Outcome.Err(TagError.TagLost), lost)
        assertTrue(tag.writeCommands.size == 1, "the second frame never reached the tag")
    }
}
