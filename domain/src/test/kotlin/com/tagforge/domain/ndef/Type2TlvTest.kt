package com.tagforge.domain.ndef

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** NC-04 wrapping and NC-05 TLV area parsing. */
class Type2TlvTest {

    private fun filled(size: Int, value: Int = 0xAB): Bytes = Bytes.copyOf(ByteArray(size) { value.toByte() })

    private fun unwrapOk(bytes: Bytes): Type2Tlv.Area {
        val result = Type2Tlv.unwrap(bytes)
        assertIs<Outcome.Ok<Type2Tlv.Area>>(result, "expected Ok but was $result")
        return result.value
    }

    private fun unwrapErr(bytes: Bytes): NdefParseError {
        val result = Type2Tlv.unwrap(bytes)
        assertIs<Outcome.Err<NdefParseError>>(result, "expected Err but was $result")
        return result.error
    }

    // ---- wrap ---------------------------------------------------------------------------------

    @Test
    fun wrapsShortMessage() {
        val hi = "D1 01 05 54 02 65 6E 68 69".hex()
        assertEquals("03 09 D1 01 05 54 02 65 6E 68 69 FE".hex(), Type2Tlv.wrap(hi))
    }

    @Test
    fun wrapBoundaryBetween254And255() {
        val short = Type2Tlv.wrap(filled(254))
        assertEquals("03 FE".hex(), short.slice(0, 2))
        assertEquals(257, short.size)

        val long = Type2Tlv.wrap(filled(255))
        assertEquals("03 FF 00 FF".hex(), long.slice(0, 4))
        assertEquals(260, long.size)
    }

    @Test
    fun wrapEmptyMessageIsZeroLengthTlv() {
        assertEquals("03 00 FE".hex(), Type2Tlv.wrap(Bytes.EMPTY))
    }

    @Test
    fun wrapKeepsLeadingTlvsInFront() {
        val lockControl = "01 03 A0 0C 34".hex()
        assertEquals("01 03 A0 0C 34 03 00 FE".hex(), Type2Tlv.wrap(Bytes.EMPTY, leading = lockControl))
    }

    // ---- unwrap -------------------------------------------------------------------------------

    @Test
    fun unwrapsNtag213FactoryAreaWithLockControlTlv() {
        val area = unwrapOk("01 03 A0 0C 34 03 00 FE".hex())
        assertEquals(Bytes.EMPTY, area.message)
        assertEquals(5, area.leadingBytes)
    }

    @Test
    fun unwrapsNtag215FactoryArea() {
        val area = unwrapOk("03 00 FE 00".hex())
        assertEquals(Bytes.EMPTY, area.message)
        assertEquals(0, area.leadingBytes)
    }

    @Test
    fun unwrapsThreeByteLength() {
        val body = filled(256)
        val area = unwrapOk("03 FF 01 00".hex() + body + "FE".hex())
        assertEquals(body, area.message)
    }

    @Test
    fun unwrapsOneByteLengthMessageAndIgnoresTrailingBytes() {
        val hi = "D1 01 05 54 02 65 6E 68 69".hex()
        val area = unwrapOk("03 09".hex() + hi + "FE 00 00 12".hex())
        assertEquals(hi, area.message)
    }

    @Test
    fun skipsNullAndMemoryControlTlvs() {
        val area = unwrapOk("00 00 02 03 F0 00 40 03 01 AA FE".hex())
        assertEquals("AA".hex(), area.message)
        assertEquals(7, area.leadingBytes)
    }

    @Test
    fun skipsProprietaryTlvByLength() {
        val area = unwrapOk("FD 02 11 22 03 01 AA FE".hex())
        assertEquals("AA".hex(), area.message)
        assertEquals(4, area.leadingBytes)
    }

    @Test
    fun tolerantOfMissingTerminatorWhenAreaIsFull() {
        assertEquals("AA".hex(), unwrapOk("03 01 AA".hex()).message)
    }

    @Test
    fun truncatedMessageFails() {
        assertEquals(NdefParseError.Truncated, unwrapErr("03 10 D1 01".hex()))
    }

    @Test
    fun truncatedLengthFieldFails() {
        assertEquals(NdefParseError.Truncated, unwrapErr("03".hex()))
        assertEquals(NdefParseError.Truncated, unwrapErr("03 FF 01".hex()))
        assertEquals(NdefParseError.Truncated, unwrapErr("01 03 A0".hex()))
    }

    @Test
    fun terminatorBeforeAnyNdefTlvMeansNoNdef() {
        assertEquals(NdefParseError.NoNdefTlv, unwrapErr("FE 00 00 00".hex()))
    }

    @Test
    fun zeroedAreaMeansNoNdef() {
        assertEquals(NdefParseError.NoNdefTlv, unwrapErr("00 00 00 00 00 00 00 00".hex()))
        assertEquals(NdefParseError.NoNdefTlv, unwrapErr(Bytes.EMPTY))
    }

    @Test
    fun reservedTlvTagIsRejected() {
        assertEquals(NdefParseError.InvalidTlv(0x05), unwrapErr("05 01 00 03 00 FE".hex()))
    }

    @Test
    fun roundTripsThroughWrap() {
        for (size in listOf(0, 1, 254, 255, 256, 1000)) {
            val body = filled(size, 0x5A)
            assertEquals(body, unwrapOk(Type2Tlv.wrap(body)).message, "size $size")
        }
    }
}
