package com.tagforge.domain.ndef.spec

/** What a person types or pastes for a Bluetooth address, turned into the one form the record accepts. */
object MacAddressInput {

    private const val DIGITS = 12
    private const val SEPARATORS = " :-."

    /** `AA:BB:CC:DD:EE:FF` from `AABBCCDDEEFF`, `aa-bb-...`, `aabb.ccdd.eeff` or spaced pairs; null unless it is exactly six bytes. */
    fun parse(input: String): String? {
        val digits = input.filter { it !in SEPARATORS }
        return if (digits.length == DIGITS && digits.all(::isHex)) group(digits.uppercase()) else null
    }

    /** Live formatting while typing: hex digits only (at most twelve), upper case, a colon after every pair. */
    fun format(input: String): String = group(input.filter(::isHex).take(DIGITS).uppercase())

    private fun group(digits: String): String = digits.chunked(2).joinToString(":")

    private fun isHex(ch: Char): Boolean = ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F'
}
