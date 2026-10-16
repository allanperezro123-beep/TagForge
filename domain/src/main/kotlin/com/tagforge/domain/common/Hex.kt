package com.tagforge.domain.common

object Hex {

    /** Parses hex tolerant of spaces and colons; returns null for odd digit counts or non-hex chars. */
    fun parse(text: String): Bytes? {
        val digits = text.filterNot { it == ' ' || it == ':' || it == '\t' || it == '\n' || it == '\r' }
        if (digits.length % 2 != 0) return null
        val out = IntArray(digits.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(digits[2 * i], 16)
            val lo = Character.digit(digits[2 * i + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = (hi shl 4) or lo
        }
        return Bytes.of(*out)
    }

    fun format(bytes: Bytes, separator: String = " "): String = bytes.toHex(separator)
}
