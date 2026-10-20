package com.tagforge.domain.location

import java.math.BigDecimal
import java.math.RoundingMode

/** How a coordinate is written into a record: plain decimal, six decimals at most (about 10 cm), no exponent, no trailing zeros. */
object CoordinateText {
    private const val DECIMALS = 6

    fun format(value: Double): String {
        val rounded = BigDecimal.valueOf(value).setScale(DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (rounded.signum() == 0) "0" else rounded.toPlainString()
    }
}
