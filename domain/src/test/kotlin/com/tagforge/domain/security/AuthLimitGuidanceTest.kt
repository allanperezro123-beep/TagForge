package com.tagforge.domain.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * WU4: what an AUTHLIM value MEANS, in attempts. NTAG21x datasheet: 000b disables the limit; 001b..111b is the maximum number
 * of consecutive negative verifications, a LINEAR count (not 2^n); once reached the password can never be used again.
 */
class AuthLimitGuidanceTest {

    @Test
    fun zeroLeavesTheLimitAsItIs() {
        assertEquals(AuthLimitMeaning.LeavesUnchanged, AuthLimitGuidance.meaning(0))
    }

    @Test
    fun oneToSevenIsThatManyWrongAttemptsNotAPowerOfTwo() {
        for (limit in 1..7) {
            assertEquals(AuthLimitMeaning.AtMostAttempts(limit), AuthLimitGuidance.meaning(limit), "AUTHLIM $limit")
        }
    }

    @Test
    fun theRecommendedValueIsTheMiddleOfTheRange() {
        assertEquals(5, AuthLimitGuidance.RECOMMENDED)
        assertTrue(AuthLimitGuidance.RECOMMENDED in 2..6, "neither the easiest to lock by accident nor the loosest")
    }

    @Test
    fun aValueThatDoesNotFitThreeBitsIsRefused() {
        assertFailsWith<IllegalArgumentException> { AuthLimitGuidance.meaning(8) }
        assertFailsWith<IllegalArgumentException> { AuthLimitGuidance.meaning(-1) }
    }
}
