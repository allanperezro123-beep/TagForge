package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WU4: an honest strength estimate for a 4-byte NTAG password. The hard ceiling is 32 bits (and it travels in clear), so the
 * best verdict is BEST_POSSIBLE, never "strong"; typed text covers fewer combinations than random bytes.
 */
class PasswordStrengthTest {

    private fun hex(vararg values: Int) = PasswordStrength.evaluate(PasswordMode.HEX, Bytes.of(*values))
    private fun text(chars: String) =
        PasswordStrength.evaluate(PasswordMode.TEXT, Bytes.of(*IntArray(chars.length) { chars[it].code }))

    @Test
    fun fourRandomLookingBytesAreTheBestAnNtagCanDo() {
        val strength = hex(0x3A, 0x9F, 0xC2, 0x71)

        assertEquals(StrengthLevel.BEST_POSSIBLE, strength.level)
        assertEquals(32, strength.estimatedBits)
        assertEquals(emptyList(), strength.reasons)
        assertEquals(32, PasswordStrength.CEILING_BITS)
    }

    @Test
    fun allTheSameByteIsWeak() {
        assertEquals(listOf(StrengthReason.ALL_ZERO), hex(0, 0, 0, 0).reasons)
        assertEquals(listOf(StrengthReason.ALL_SAME_BYTE), hex(0x41, 0x41, 0x41, 0x41).reasons)
        assertEquals(StrengthLevel.WEAK, hex(0, 0, 0, 0).level)
        assertEquals(StrengthLevel.WEAK, hex(0x41, 0x41, 0x41, 0x41).level)
    }

    @Test
    fun theFactoryValueIsRecognisedAsTheFactoryDefault() {
        val strength = hex(0xFF, 0xFF, 0xFF, 0xFF)

        assertEquals(StrengthLevel.WEAK, strength.level)
        assertTrue(StrengthReason.FACTORY_DEFAULT in strength.reasons)
    }

    @Test
    fun sequentialBytesUpOrDownAreWeakInBothModes() {
        assertEquals(listOf(StrengthReason.SEQUENTIAL_BYTES), hex(0x01, 0x02, 0x03, 0x04).reasons)
        assertEquals(listOf(StrengthReason.SEQUENTIAL_BYTES), hex(0x04, 0x03, 0x02, 0x01).reasons)
        assertTrue(StrengthReason.SEQUENTIAL_BYTES in text("EFGH").reasons)
        assertEquals(StrengthLevel.WEAK, text("EFGH").level)
    }

    @Test
    fun aRepeatedPairIsWeak() {
        val strength = hex(0x12, 0x34, 0x12, 0x34)

        assertEquals(StrengthLevel.WEAK, strength.level)
        assertTrue(StrengthReason.REPEATED_PAIR in strength.reasons)
    }

    @Test
    fun theUsualSuspectsAreWeak() {
        for (known in listOf(intArrayOf(0x12, 0x34, 0x56, 0x78), intArrayOf(0xDE, 0xAD, 0xBE, 0xEF), intArrayOf(0x11, 0x22, 0x33, 0x44))) {
            val strength = hex(*known)
            assertEquals(StrengthLevel.WEAK, strength.level, known.joinToString())
            assertTrue(StrengthReason.COMMON_PATTERN in strength.reasons, known.joinToString())
        }
        for (word in listOf("pass", "Pass", "qwer", "admin".take(4), "0000", "1111")) {
            assertTrue(StrengthReason.COMMON_PATTERN in text(word).reasons || StrengthReason.ALL_SAME_BYTE in text(word).reasons, word)
            assertEquals(StrengthLevel.WEAK, text(word).level, word)
        }
    }

    @Test
    fun aPatternIsGuessedInAFewTriesNotInThirtyTwoBits() {
        assertTrue(hex(0xDE, 0xAD, 0xBE, 0xEF).estimatedBits <= 8)
        assertTrue(hex(0, 0, 0, 0).estimatedBits <= 8)
    }

    @Test
    fun typedDigitsOnlyAreAboutThirteenBitsAndWeak() {
        val strength = text("7391")

        assertEquals(13, strength.estimatedBits)
        assertEquals(StrengthLevel.WEAK, strength.level)
        assertTrue(StrengthReason.ONLY_DIGITS in strength.reasons)
    }

    @Test
    fun typedLowercaseOrUppercaseOnlyAreAboutNineteenBitsAndWeak() {
        val lower = text("kqzp")
        val upper = text("KQZP")

        assertEquals(19, lower.estimatedBits)
        assertEquals(19, upper.estimatedBits)
        assertEquals(StrengthLevel.WEAK, lower.level)
        assertEquals(StrengthLevel.WEAK, upper.level)
        assertTrue(StrengthReason.ONLY_LOWERCASE in lower.reasons)
        assertTrue(StrengthReason.ONLY_UPPERCASE in upper.reasons)
    }

    @Test
    fun typedMixedCharactersAreFairAtBestNeverBestPossible() {
        val mixed = text("aB3$")
        val lettersAndDigits = text("k7q2")

        assertEquals(26, mixed.estimatedBits)
        assertEquals(StrengthLevel.FAIR, mixed.level)
        assertTrue(StrengthReason.TEXT_SMALLER_SPACE in mixed.reasons, "typed text covers fewer combinations than random bytes")
        assertEquals(21, lettersAndDigits.estimatedBits)
        assertEquals(StrengthLevel.FAIR, lettersAndDigits.level)
        assertFalse(text("aB3$").level == StrengthLevel.BEST_POSSIBLE)
    }

    @Test
    fun hexDigitsThatHappenToBeAllDecimalAreFairNotBest() {
        val strength = hex(0x19, 0x84, 0x03, 0x12)

        assertEquals(27, strength.estimatedBits)
        assertEquals(StrengthLevel.FAIR, strength.level)
        assertTrue(StrengthReason.ONLY_DIGITS in strength.reasons)
    }

    @Test
    fun noVerdictEverExceedsTheThirtyTwoBitCeiling() {
        val random = kotlin.random.Random(5)
        repeat(200) {
            val bytes = Bytes.copyOf(random.nextBytes(4))
            for (mode in PasswordMode.entries) {
                assertTrue(PasswordStrength.evaluate(mode, bytes).estimatedBits <= PasswordStrength.CEILING_BITS)
            }
        }
    }
}
