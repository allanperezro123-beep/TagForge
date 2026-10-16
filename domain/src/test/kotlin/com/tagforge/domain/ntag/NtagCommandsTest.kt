package com.tagforge.domain.ntag

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.tag.TagError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NtagCommandsTest {

    // ---- builders -----------------------------------------------------------------------------

    @Test
    fun getVersionIsSingleByte60() {
        assertEquals("60".hex(), NtagCommands.getVersion())
    }

    @Test
    fun readAddressesOnePage() {
        assertEquals("30 04".hex(), NtagCommands.read(4))
        assertEquals("30 E6".hex(), NtagCommands.read(230))
    }

    @Test
    fun fastReadCarriesStartAndEnd() {
        assertEquals("3A 00 2C".hex(), NtagCommands.fastRead(0, 44))
        assertEquals("3A 04 07".hex(), NtagCommands.fastRead(4, 7))
    }

    @Test
    fun writeCarriesAddressAndFourDataBytes() {
        assertEquals("A2 04 D1 01 05 54".hex(), NtagCommands.write(4, "D1 01 05 54".hex()))
    }

    @Test
    fun buildersRejectOutOfRangeArguments() {
        assertFailsWith<IllegalArgumentException> { NtagCommands.read(256) }
        assertFailsWith<IllegalArgumentException> { NtagCommands.read(-1) }
        assertFailsWith<IllegalArgumentException> { NtagCommands.fastRead(5, 4) }
        assertFailsWith<IllegalArgumentException> { NtagCommands.write(4, "01 02 03".hex()) }
    }

    // ---- GET_VERSION --------------------------------------------------------------------------

    @Test
    fun versionBytesIdentifyEachModel() {
        assertEquals(NtagModel.NTAG213, NtagResponses.parseVersion("00 04 04 02 01 00 0F 03".hex()))
        assertEquals(NtagModel.NTAG215, NtagResponses.parseVersion("00 04 04 02 01 00 11 03".hex()))
        assertEquals(NtagModel.NTAG216, NtagResponses.parseVersion("00 04 04 02 01 00 13 03".hex()))
    }

    @Test
    fun unknownOrMalformedVersionReturnsNull() {
        assertNull(NtagResponses.parseVersion("00 04 04 02 01 00 12 03".hex()))   // unknown storage size
        assertNull(NtagResponses.parseVersion("00 04 03 01 01 00 0B 03".hex()))   // Ultralight EV1, not NTAG
        assertNull(NtagResponses.parseVersion("00 05 04 02 01 00 11 03".hex()))   // not NXP
        assertNull(NtagResponses.parseVersion("00 04 04 02".hex()))               // too short
        assertNull(NtagResponses.parseVersion(Bytes.EMPTY))
    }

    // ---- NAK / ACK ----------------------------------------------------------------------------

    @Test
    fun nakCodesFromTheDatasheetAreDecoded() {
        for (code in listOf(0x0, 0x1, 0x4, 0x5)) {
            assertEquals(Outcome.Err(TagError.Nak(code)), NtagResponses.checkAck(Bytes.of(code)), "NAK $code")
        }
    }

    @Test
    fun ackIsSingleByteA() {
        assertEquals(Outcome.Ok(Unit), NtagResponses.checkAck(Bytes.of(0x0A)))
    }

    @Test
    fun ackWithUnexpectedContentIsMalformed() {
        assertEquals(Outcome.Err(TagError.Malformed("Unexpected response length 0")), NtagResponses.checkAck(Bytes.EMPTY))
        assertEquals(Outcome.Err(TagError.Malformed("Unexpected ACK byte 0x07")), NtagResponses.checkAck(Bytes.of(0x07)))
    }

    @Test
    fun readResponseMustMatchExpectedLength() {
        val sixteen = Bytes.copyOf(ByteArray(16) { it.toByte() })
        assertEquals(Outcome.Ok(sixteen), NtagResponses.checkData(sixteen, expectedLength = 16))
        assertEquals(
            Outcome.Err(TagError.Malformed("Expected 16 bytes but received 3")),
            NtagResponses.checkData("01 02 03".hex(), expectedLength = 16),
        )
    }

    @Test
    fun dataResponseThatIsANakIsReportedAsNak() {
        assertEquals(Outcome.Err(TagError.Nak(0)), NtagResponses.checkData(Bytes.of(0x00), expectedLength = 16))
        assertEquals(Outcome.Err(TagError.Nak(5)), NtagResponses.checkData(Bytes.of(0x05), expectedLength = 4))
    }
}
