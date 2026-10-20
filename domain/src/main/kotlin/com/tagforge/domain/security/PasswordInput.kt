package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Hex
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.ntag.NtagCommands
import kotlin.random.Random

/** How the user types a secret: as ordinary text (the default) or, for tags protected by other apps, as hexadecimal. */
enum class PasswordMode { TEXT, HEX }

/** Which secret is being typed. */
enum class SecretKind(val byteCount: Int) {
    PASSWORD(NtagCommands.PASSWORD_SIZE),
    PACK(NtagCommands.PACK_SIZE),
}

/** Why typed input is not (yet) a valid secret. Typed values, so the UI can phrase each one in plain language. */
sealed interface PasswordInputError {
    data object Empty : PasswordInputError

    /** [expected] and [actual] count characters in TEXT mode and hex digits in HEX mode. */
    data class WrongLength(val expected: Int, val actual: Int) : PasswordInputError

    data object NonAscii : PasswordInputError
    data object HasSpace : PasswordInputError
    data object NotHex : PasswordInputError
    data object FactoryValue : PasswordInputError
}

/**
 * Pure conversion between what a person types and the bytes an NTAG password/PACK needs. An NTAG password is exactly 4
 * bytes, so in TEXT mode it is exactly 4 ASCII characters (the same convention as other NFC apps); HEX mode exists for tags
 * protected elsewhere with bytes that have no keyboard character.
 */
object PasswordInput {

    private const val FIRST_PRINTABLE = 0x21
    private const val LAST_PRINTABLE = 0x7E
    private const val HEX_RADIX = 16

    /** Without look-alikes (0 O 1 l I): a generated password is read from a screen and typed back. */
    private const val GENERATED_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
    private const val DEFAULT_PACK_TEXT = "TF"

    fun capacity(mode: PasswordMode, kind: SecretKind): Int =
        if (mode == PasswordMode.TEXT) kind.byteCount else kind.byteCount * 2

    /** What the live counter shows: characters in TEXT mode, hex digits (separators ignored) in HEX mode. */
    fun count(mode: PasswordMode, input: String): Int =
        if (mode == PasswordMode.TEXT) input.length else input.count { !isSeparator(it) }

    fun parse(mode: PasswordMode, kind: SecretKind, input: String): Outcome<Bytes, PasswordInputError> =
        when (mode) {
            PasswordMode.TEXT -> parseText(kind, input)
            PasswordMode.HEX -> parseHex(kind, input)
        }

    /** A password: [parse] plus the refusal of the factory value `FFFFFFFF`. */
    fun password(mode: PasswordMode, input: String): Outcome<Password, PasswordInputError> =
        when (val parsed = parse(mode, SecretKind.PASSWORD, input)) {
            is Outcome.Err -> parsed
            is Outcome.Ok -> Password.of(parsed.value)?.let { Outcome.Ok(it) } ?: Outcome.Err(PasswordInputError.FactoryValue)
        }

    /** What a field keeps while typing: capped, and in HEX mode upper-cased without separators. Wrong characters stay visible. */
    fun sanitize(mode: PasswordMode, kind: SecretKind, raw: String): String {
        val cleaned = if (mode == PasswordMode.HEX) raw.filterNot(::isSeparator).uppercase() else raw
        return cleaned.take(capacity(mode, kind))
    }

    /** [bytes] as typed in [mode]; null when TEXT cannot show them (a byte with no keyboard character). */
    fun render(mode: PasswordMode, bytes: Bytes): String? = when (mode) {
        PasswordMode.HEX -> bytes.toHex()
        PasswordMode.TEXT -> {
            val chars = (0 until bytes.size).map { bytes[it] }
            if (chars.all { it in FIRST_PRINTABLE..LAST_PRINTABLE }) chars.joinToString("") { it.toChar().toString() } else null
        }
    }

    /** Keeps what was typed when the user flips the switch; empty when the value has no form in the other mode. */
    fun convert(from: PasswordMode, to: PasswordMode, input: String): String {
        if (from == to) return input
        return when (to) {
            PasswordMode.HEX -> if (input.all { it.code in FIRST_PRINTABLE..LAST_PRINTABLE }) {
                input.map { it.code.toString(HEX_RADIX).padStart(2, '0') }.joinToString("").uppercase()
            } else {
                ""
            }
            PasswordMode.TEXT -> Hex.parse(input)?.let { render(PasswordMode.TEXT, it) } ?: ""
        }
    }

    fun generate(mode: PasswordMode, random: Random): String = when (mode) {
        PasswordMode.TEXT -> randomText(SecretKind.PASSWORD.byteCount, random)
        PasswordMode.HEX -> Password.random(random).hex
    }

    fun generatePack(mode: PasswordMode, random: Random): String = when (mode) {
        PasswordMode.TEXT -> randomText(SecretKind.PACK.byteCount, random)
        PasswordMode.HEX -> Password.randomPack(random).toHex()
    }

    /** "TF" (Tag Forge): a readable PACK, so the user never has to think about it. */
    fun defaultPack(mode: PasswordMode): String =
        if (mode == PasswordMode.TEXT) DEFAULT_PACK_TEXT else Bytes.copyOf(DEFAULT_PACK_TEXT.toByteArray(Charsets.US_ASCII)).toHex()

    /** "12 34 56 78": hex digits grouped by byte, for display. */
    fun groupHex(digits: String): String = digits.chunked(2).joinToString(" ")

    private fun randomText(length: Int, random: Random): String =
        buildString { repeat(length) { append(GENERATED_ALPHABET[random.nextInt(GENERATED_ALPHABET.length)]) } }

    private fun isSeparator(c: Char): Boolean = c == ' ' || c == ':' || c == '-' || c == '\t'

    private fun parseText(kind: SecretKind, input: String): Outcome<Bytes, PasswordInputError> {
        if (input.isEmpty()) return Outcome.Err(PasswordInputError.Empty)
        if (input.any { it == ' ' }) return Outcome.Err(PasswordInputError.HasSpace)
        if (input.any { it.code !in FIRST_PRINTABLE..LAST_PRINTABLE }) return Outcome.Err(PasswordInputError.NonAscii)
        if (input.length != kind.byteCount) return Outcome.Err(PasswordInputError.WrongLength(kind.byteCount, input.length))
        return Outcome.Ok(Bytes.of(*IntArray(input.length) { input[it].code }))
    }

    private fun parseHex(kind: SecretKind, input: String): Outcome<Bytes, PasswordInputError> {
        val digits = input.filterNot(::isSeparator)
        if (digits.isEmpty()) return Outcome.Err(PasswordInputError.Empty)
        if (digits.any { Character.digit(it, HEX_RADIX) < 0 }) return Outcome.Err(PasswordInputError.NotHex)
        val expected = capacity(PasswordMode.HEX, kind)
        if (digits.length != expected) return Outcome.Err(PasswordInputError.WrongLength(expected, digits.length))
        return Hex.parse(digits)?.let { Outcome.Ok(it) } ?: Outcome.Err(PasswordInputError.NotHex)
    }
}
