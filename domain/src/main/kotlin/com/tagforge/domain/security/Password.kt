package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Hex
import com.tagforge.domain.ntag.NtagCommands
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * A 32-bit NTAG password (NS-02): exactly 4 bytes, typed as 8 hex digits or generated randomly.
 * `FFFFFFFF` is refused: it is the factory value of an unprotected tag. [toString] never prints the value.
 */
@JvmInline
value class Password private constructor(val bytes: Bytes) {

    val hex: String get() = bytes.toHex()

    override fun toString(): String = "Password(****)"

    companion object {
        private val FACTORY = Bytes.of(0xFF, 0xFF, 0xFF, 0xFF)

        fun of(bytes: Bytes): Password? =
            if (bytes.size == NtagCommands.PASSWORD_SIZE && bytes != FACTORY) Password(bytes) else null

        /** [text] is hex with optional spaces; null unless it is exactly 8 hex digits and not the factory value. */
        fun fromHex(text: String): Password? = Hex.parse(text)?.let(::of)

        fun random(random: Random = SecureRandom().asKotlinRandom()): Password {
            while (true) {
                of(Bytes.copyOf(random.nextBytes(NtagCommands.PASSWORD_SIZE)))?.let { return it }
            }
        }

        /** A random 2-byte PACK. */
        fun randomPack(random: Random = SecureRandom().asKotlinRandom()): Bytes =
            Bytes.copyOf(random.nextBytes(NtagCommands.PACK_SIZE))
    }
}
