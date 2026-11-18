package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.ntag.NtagCommands
import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Why a master passphrase is refused. Typed so the UI phrases each one in the user's language. */
sealed interface PassphraseProblem {
    data class TooShort(val minimum: Int, val actual: Int) : PassphraseProblem
    data object TooFewDistinctCharacters : PassphraseProblem
    data object Repetitive : PassphraseProblem
    data object Sequential : PassphraseProblem
    data object Common : PassphraseProblem
}

/**
 * UID-diversified NTAG passwords (WU4), for a fleet of tags protected by ONE master passphrase.
 *
 * `HMAC-SHA256(key = UTF-8(NFC(passphrase)), message = "TagForge-NTAG-PWD-v1" || UID)`: PWD is bytes 0..3 and PACK bytes
 * 4..5. Each tag gets its own password and PACK, so cracking one tag (32 bits, sent in clear) does not open the others; the
 * passphrase itself never travels. NFC normalisation makes an accented passphrase identical whichever way the keyboard
 * composed it. If bytes 0..3 were the factory value `FFFFFFFF` (probability 2^-32), the derivation is repeated with the
 * message extended by one counter byte (1, 2, ...); counter 0 is the plain message above.
 *
 * Pure: no Android, no storage. The passphrase is an argument, never kept here.
 */
object DiversifiedPassword {

    const val MIN_PASSPHRASE_LENGTH = 12

    private const val CONTEXT = "TagForge-NTAG-PWD-v1"
    private const val HMAC = "HmacSHA256"
    private const val MIN_DISTINCT = 5
    private const val MIN_PERIOD = 2
    private const val SEQUENTIAL_SHARE = 0.75
    private val COMMON_WORDS = listOf(
        "password", "passphrase", "qwertyuiop", "qwerty", "iloveyou", "letmein", "administrator", "welcome",
        "tagforge", "contrasena", "secret", "football", "dragon", "monkey",
    )

    /** Derives PWD and PACK for the tag with [uid]. [passphrase] must not be empty; use [deriveChecked] for user input. */
    fun derive(passphrase: String, uid: Bytes): Pair<Password, Bytes> {
        require(passphrase.isNotEmpty()) { "The master passphrase cannot be empty" }
        val key = SecretKeySpec(Normalizer.normalize(passphrase, Normalizer.Form.NFC).toByteArray(Charsets.UTF_8), HMAC)
        val message = CONTEXT.toByteArray(Charsets.US_ASCII) + uid.toByteArray()
        val blocks = generateSequence(0) { it + 1 }.map { counter ->
            val mac = Mac.getInstance(HMAC)
            mac.init(key)
            mac.doFinal(if (counter == 0) message else message + counter.toByte())
        }
        return pick(blocks)
    }

    /** [derive] for what a person typed: refused, with the reason, when the passphrase is too weak to protect a fleet. */
    fun deriveChecked(passphrase: String, uid: Bytes): Outcome<Pair<Password, Bytes>, PassphraseProblem> =
        check(passphrase)?.let { Outcome.Err(it) } ?: Outcome.Ok(derive(passphrase, uid))

    /** The first block whose PWD is not the factory value. */
    internal fun pick(blocks: Sequence<ByteArray>): Pair<Password, Bytes> {
        for (block in blocks) {
            val password = Password.of(Bytes.copyOf(block.copyOfRange(0, NtagCommands.PASSWORD_SIZE))) ?: continue
            val pack = Bytes.copyOf(block.copyOfRange(NtagCommands.PASSWORD_SIZE, NtagCommands.PASSWORD_SIZE + NtagCommands.PACK_SIZE))
            return password to pack
        }
        error("unreachable: the block sequence is infinite")
    }

    /** Null when [passphrase] may be used: long enough and not an obvious pattern. */
    fun check(passphrase: String): PassphraseProblem? {
        val text = Normalizer.normalize(passphrase, Normalizer.Form.NFC)
        val length = text.codePointCount(0, text.length)
        return when {
            length < MIN_PASSPHRASE_LENGTH -> PassphraseProblem.TooShort(MIN_PASSPHRASE_LENGTH, length)
            isCommon(text) -> PassphraseProblem.Common
            isPeriodic(text) -> PassphraseProblem.Repetitive
            text.toSet().size < MIN_DISTINCT -> PassphraseProblem.TooFewDistinctCharacters
            isSequential(text) -> PassphraseProblem.Sequential
            else -> null
        }
    }

    /** The whole text is one short unit repeated ("abcabcabc..."). A single repeated character is counted as "few distinct". */
    private fun isPeriodic(text: String): Boolean =
        text.any { it != text[0] } &&
            (MIN_PERIOD..text.length / 2).any { period -> (period until text.length).all { text[it] == text[it - period] } }

    /** Nearly every step goes one character up or down ("abcdefgh...", "1234567890123"). */
    private fun isSequential(text: String): Boolean {
        val steps = (1 until text.length).count { kotlin.math.abs(text[it].code - text[it - 1].code) == 1 }
        return steps >= (text.length - 1) * SEQUENTIAL_SHARE
    }

    /** Only letters, once the digits and symbols are dropped, and all of them well-known words. */
    private fun isCommon(text: String): Boolean {
        var letters = text.filter { it.isLetter() }.lowercase()
        if (letters.isEmpty()) return false
        COMMON_WORDS.forEach { letters = letters.replace(it, "") }
        return letters.isEmpty()
    }
}
