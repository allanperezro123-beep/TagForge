package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import kotlin.math.ln
import kotlin.math.roundToInt

/** How much an NTAG password resists a guess. There is no "strong": the ceiling is [PasswordStrength.CEILING_BITS]. */
enum class StrengthLevel { WEAK, FAIR, BEST_POSSIBLE }

/** Why a password was not rated higher. Typed, so the UI phrases each one in the user's language. */
enum class StrengthReason {
    ALL_ZERO,
    ALL_SAME_BYTE,
    FACTORY_DEFAULT,
    SEQUENTIAL_BYTES,
    REPEATED_PAIR,
    COMMON_PATTERN,
    ONLY_DIGITS,
    ONLY_LOWERCASE,
    ONLY_UPPERCASE,

    /** Typed text: informational, four characters cover fewer combinations than four random bytes. */
    TEXT_SMALLER_SPACE,
}

/**
 * Pure strength estimate of a 4-byte NTAG password (WU4). [estimatedBits] is a guessing-space estimate, never above
 * [CEILING_BITS]: an NTAG21x password is 32 bits and PWD_AUTH sends it in clear, so even the best value stops casual
 * rewriting, not a determined attacker holding a reader.
 */
data class PasswordStrength(val level: StrengthLevel, val estimatedBits: Int, val reasons: List<StrengthReason>) {

    companion object {
        /** The whole password space: 4 bytes. */
        const val CEILING_BITS = 32

        /** A pattern is tried in the first guesses, whatever its length. */
        private const val PATTERN_BITS = 8
        private const val WEAK_BELOW_BITS = 20
        private const val BITS_PER_BYTE = 8
        private const val DIGIT_CLASS = 10
        private const val LETTER_CLASS = 26
        private const val SYMBOL_CLASS = 32
        private const val FIRST_PRINTABLE = 0x21
        private const val LAST_PRINTABLE = 0x7E
        private const val FACTORY_BYTE = 0xFF

        private val COMMON_HEX = setOf(
            "12345678", "87654321", "01234567", "76543210", "11223344", "44332211", "DEADBEEF", "CAFEBABE",
            "0BADF00D", "55AA55AA", "AA55AA55", "ABCDEF12", "00000001", "10203040", "A1B2C3D4",
        )
        private val COMMON_TEXT = setOf("1234", "4321", "0000", "1111", "abcd", "qwer", "asdf", "pass", "admi", "admin", "test", "1212", "2580", "root", "user", "love")

        fun evaluate(mode: PasswordMode, bytes: Bytes): PasswordStrength {
            val values = IntArray(bytes.size) { bytes[it] }
            val asText = mode == PasswordMode.TEXT && values.all { it in FIRST_PRINTABLE..LAST_PRINTABLE }
            val reasons = patterns(values, asText).toMutableList()
            val isPattern = reasons.isNotEmpty()
            val bits = when {
                isPattern -> PATTERN_BITS
                asText -> textBits(values, reasons)
                else -> hexBits(values, reasons)
            }
            if (asText) reasons += StrengthReason.TEXT_SMALLER_SPACE
            val level = when {
                isPattern || bits < WEAK_BELOW_BITS -> StrengthLevel.WEAK
                !asText && bits >= CEILING_BITS && reasons.isEmpty() -> StrengthLevel.BEST_POSSIBLE
                else -> StrengthLevel.FAIR
            }
            return PasswordStrength(level, bits.coerceAtMost(CEILING_BITS), reasons)
        }

        private fun patterns(v: IntArray, asText: Boolean): List<StrengthReason> {
            val found = mutableListOf<StrengthReason>()
            val allSame = v.isNotEmpty() && v.all { it == v[0] }
            when {
                allSame && v[0] == 0 -> found += StrengthReason.ALL_ZERO
                allSame -> found += StrengthReason.ALL_SAME_BYTE
            }
            if (allSame && v[0] == FACTORY_BYTE) found += StrengthReason.FACTORY_DEFAULT
            if (!allSame && isSequential(v)) found += StrengthReason.SEQUENTIAL_BYTES
            if (v.size == 4 && !allSame && v[0] == v[2] && v[1] == v[3]) found += StrengthReason.REPEATED_PAIR
            if (isCommon(v, asText)) found += StrengthReason.COMMON_PATTERN
            return found
        }

        private fun isSequential(v: IntArray): Boolean {
            if (v.size < 2) return false
            val step = v[1] - v[0]
            return (step == 1 || step == -1) && (1 until v.size).all { v[it] - v[it - 1] == step }
        }

        private fun isCommon(v: IntArray, asText: Boolean): Boolean {
            if (asText) return String(CharArray(v.size) { v[it].toChar() }).lowercase() in COMMON_TEXT
            return Bytes.of(*v).toHex() in COMMON_HEX
        }

        /** Guessing space by the character classes that appear: digits 10, each letter case 26, symbols 32. */
        private fun textBits(v: IntArray, reasons: MutableList<StrengthReason>): Int {
            val digits = v.any { it.toChar() in '0'..'9' }
            val lower = v.any { it.toChar() in 'a'..'z' }
            val upper = v.any { it.toChar() in 'A'..'Z' }
            val symbols = v.any { !it.toChar().isLetterOrDigit() }
            val alphabet = (if (digits) DIGIT_CLASS else 0) + (if (lower) LETTER_CLASS else 0) +
                (if (upper) LETTER_CLASS else 0) + (if (symbols) SYMBOL_CLASS else 0)
            when (alphabet) {
                DIGIT_CLASS -> reasons += StrengthReason.ONLY_DIGITS
                LETTER_CLASS -> reasons += if (lower) StrengthReason.ONLY_LOWERCASE else StrengthReason.ONLY_UPPERCASE
            }
            return log2Bits(v.size, alphabet)
        }

        private fun log2Bits(count: Int, alphabet: Int): Int = (count * ln(alphabet.toDouble()) / ln(2.0)).roundToInt()

        private fun hexBits(v: IntArray, reasons: MutableList<StrengthReason>): Int {
            val digitsOnly = v.all { (it ushr 4) <= 9 && (it and 0x0F) <= 9 }
            if (!digitsOnly) return v.size * BITS_PER_BYTE
            reasons += StrengthReason.ONLY_DIGITS
            return log2Bits(v.size * 2, DIGIT_CLASS)
        }
    }
}
