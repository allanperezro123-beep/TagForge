package com.tagforge.domain.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BytesTest {

    @Test
    fun equalityAndHashAreContentBased() {
        assertEquals(Bytes.of(1, 2, 3), Bytes.of(1, 2, 3))
        assertEquals(Bytes.of(1, 2, 3).hashCode(), Bytes.of(1, 2, 3).hashCode())
        assertNotEquals(Bytes.of(1, 2, 3), Bytes.of(1, 2, 4))
        assertNotEquals(Bytes.of(1, 2, 3), Bytes.of(1, 2))
    }

    @Test
    fun isImmutableAgainstSourceAndOutputArrays() {
        val source = byteArrayOf(1, 2, 3)
        val bytes = Bytes.copyOf(source)
        source[0] = 9
        assertEquals(1, bytes[0])

        val exported = bytes.toByteArray()
        exported[1] = 9
        assertEquals(2, bytes[1])
    }

    @Test
    fun indexingReturnsUnsignedValues() {
        assertEquals(0xE1, Bytes.of(0xE1, 0x10)[0])
        assertEquals(0x10, Bytes.of(0xE1, 0x10)[1])
        assertEquals(0xFF, Bytes.of(0xFF)[0])
    }

    @Test
    fun hexIsUppercaseWithOptionalSeparator() {
        val bytes = Bytes.of(0xD1, 0x01, 0x0A, 0xFF)
        assertEquals("D1010AFF", bytes.toHex())
        assertEquals("D1 01 0A FF", bytes.toHex(" "))
        assertEquals("D1:01:0A:FF", bytes.toHex(":"))
        assertEquals("", Bytes.EMPTY.toHex(" "))
    }

    @Test
    fun sliceReturnsTheRequestedRange() {
        val bytes = Bytes.of(10, 11, 12, 13, 14)
        assertEquals(Bytes.of(11, 12), bytes.slice(1, 3))
        assertEquals(Bytes.of(13, 14), bytes.slice(3))
        assertEquals(Bytes.EMPTY, bytes.slice(2, 2))
        assertFailsWith<IndexOutOfBoundsException> { bytes.slice(2, 9) }
        assertFailsWith<IndexOutOfBoundsException> { bytes.slice(4, 2) }
    }

    @Test
    fun concatenationPreservesOrder() {
        assertEquals(Bytes.of(1, 2, 3, 4), Bytes.of(1, 2) + Bytes.of(3, 4))
        assertEquals(Bytes.of(1, 2), Bytes.of(1, 2) + Bytes.EMPTY)
        assertEquals(Bytes.of(7, 8, 9), Bytes.concat(listOf(Bytes.of(7), Bytes.of(8), Bytes.of(9))))
    }

    @Test
    fun sizeAndEmptiness() {
        assertEquals(3, Bytes.of(1, 2, 3).size)
        assertTrue(Bytes.EMPTY.isEmpty())
        assertFalse(Bytes.of(0).isEmpty())
    }

    @Test
    fun startsWithComparesPrefix() {
        assertTrue(Bytes.of(1, 2, 3).startsWith(Bytes.of(1, 2)))
        assertFalse(Bytes.of(1, 2, 3).startsWith(Bytes.of(2, 3)))
        assertFalse(Bytes.of(1).startsWith(Bytes.of(1, 2)))
    }

    @Test
    fun ofRejectsValuesOutsideByteRange() {
        assertFailsWith<IllegalArgumentException> { Bytes.of(256) }
        assertFailsWith<IllegalArgumentException> { Bytes.of(-1) }
    }

    @Test
    fun indexOfFindsFirstOccurrence() {
        assertEquals(2, Bytes.of(0, 1, 0xFE, 3, 0xFE).indexOf(0xFE))
        assertEquals(-1, Bytes.of(0, 1).indexOf(0xFE))
    }
}
