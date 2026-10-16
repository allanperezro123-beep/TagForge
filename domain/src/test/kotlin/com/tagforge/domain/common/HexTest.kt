package com.tagforge.domain.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HexTest {

    @Test
    fun parsesPlainSpacedAndColonSeparatedHex() {
        assertEquals(Bytes.of(0xD1, 0x01, 0x05), Hex.parse("D10105"))
        assertEquals(Bytes.of(0xD1, 0x01, 0x05), Hex.parse("d1 01 05"))
        assertEquals(Bytes.of(0x04, 0xA2, 0x3B), Hex.parse("04:A2:3B"))
    }

    @Test
    fun emptyInputIsEmptyBytes() {
        assertEquals(Bytes.EMPTY, Hex.parse(""))
        assertEquals(Bytes.EMPTY, Hex.parse("  "))
    }

    @Test
    fun invalidInputReturnsNull() {
        assertNull(Hex.parse("D10"))      // odd digit count
        assertNull(Hex.parse("ZZ"))       // not hex
        assertNull(Hex.parse("D1 0G"))
    }

    @Test
    fun formatRoundTripsWithParse() {
        val bytes = Bytes.of(0x00, 0x7F, 0x80, 0xFF)
        assertEquals("00 7F 80 FF", Hex.format(bytes))
        assertEquals(bytes, Hex.parse(Hex.format(bytes)))
        assertEquals("00:7F:80:FF", Hex.format(bytes, ":"))
    }
}
