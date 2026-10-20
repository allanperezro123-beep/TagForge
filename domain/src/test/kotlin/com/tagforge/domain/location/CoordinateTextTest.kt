package com.tagforge.domain.location

import kotlin.test.Test
import kotlin.test.assertEquals

class CoordinateTextTest {

    @Test
    fun aCoordinateKeepsAtMostSixDecimalsWithoutTrailingZeros() {
        assertEquals("4.711", CoordinateText.format(4.7110001))
        assertEquals("-74.072135", CoordinateText.format(-74.07213456789))
        assertEquals("40.5", CoordinateText.format(40.5))
    }

    @Test
    fun zeroAndTinyValuesNeverTurnIntoScientificNotationOrNegativeZero() {
        assertEquals("0", CoordinateText.format(0.0))
        assertEquals("0", CoordinateText.format(-0.0000001))
        assertEquals("10", CoordinateText.format(10.0))
    }
}
