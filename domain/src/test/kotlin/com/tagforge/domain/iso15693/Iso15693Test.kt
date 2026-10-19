package com.tagforge.domain.iso15693

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeIso15693
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.tag.TagError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class Iso15693Test {

    private val uid = FakeIso15693.ICODE_UID

    @Test
    fun readSingleBlockIsAnAddressedFrameWithTheUidAndTheBlockNumber() {
        assertEquals("22 20 11 22 33 44 55 01 04 E0 07".hex(), Iso15693.readSingleBlock(uid, 7))
    }

    @Test
    fun getSystemInfoIsAnAddressedFrameWithTheUid() {
        assertEquals("22 2B 11 22 33 44 55 01 04 E0".hex(), Iso15693.getSystemInfo(uid))
    }

    @Test
    fun singleBlockCommandsAddressAtMost256Blocks() {
        assertFailsWith<IllegalArgumentException> { Iso15693.readSingleBlock(uid, 256) }
        assertFailsWith<IllegalArgumentException> { Iso15693.readSingleBlock(Bytes.of(1, 2), 0) }
    }

    @Test
    fun theManufacturerComesFromTheByteBeforeTheE0Prefix() {
        assertEquals(0x04, Iso15693.manufacturerCode(FakeIso15693.ICODE_UID))
        assertEquals("NXP", Iso15693.manufacturerName(FakeIso15693.ICODE_UID))
        assertEquals("STMicroelectronics", Iso15693.manufacturerName(FakeIso15693.ST_UID))
    }

    @Test
    fun aUidWithoutTheE0PrefixHasNoManufacturer() {
        assertNull(Iso15693.manufacturerCode(Bytes.of(1, 2, 3, 4)))
        assertNull(Iso15693.manufacturerCode("11 22 33 44 55 01 04 E1".hex()))
    }

    @Test
    fun theDisplayedUidStartsWithE0() {
        assertEquals("E0 04 01 55 44 33 22 11".hex(), Iso15693.displayUid(uid))
    }

    @Test
    fun systemInfoReportsDsfidAfiBlockCountAndSizeWhenAnnounced() {
        val response = "00 0F".hex() + uid + "05 06 4F 03 01".hex()
        assertEquals(Iso15693.SystemInfo(dsfId = 5, afi = 6, blockCount = 80, blockSize = 4, icReference = 1), Iso15693.parseSystemInfo(response))
    }

    @Test
    fun systemInfoOnlyReadsTheFieldsTheInfoFlagsAnnounce() {
        val response = "00 04".hex() + uid + "7F 1F".hex()
        assertEquals(Iso15693.SystemInfo(null, null, blockCount = 128, blockSize = 32, icReference = null), Iso15693.parseSystemInfo(response))
    }

    @Test
    fun anErrorOrTruncatedSystemInfoAnswerIsNotParsed() {
        assertNull(Iso15693.parseSystemInfo("01 01".hex()))
        assertNull(Iso15693.parseSystemInfo("00 0F".hex() + uid.slice(0, 4)))
    }

    @Test
    fun blockDataDropsTheFlagByteAndMapsTheErrorFlagToNak() {
        assertEquals(Outcome.Ok("AA BB CC DD".hex()), Iso15693.blockData("00 AA BB CC DD".hex()))
        assertEquals(Outcome.Err(TagError.Nak(0x10)), Iso15693.blockData("01 10".hex()))
        assertEquals(Outcome.Err(TagError.Malformed("Empty ISO 15693 answer")), Iso15693.blockData(Bytes.EMPTY))
    }
}
