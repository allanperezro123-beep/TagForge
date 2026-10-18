package com.tagforge.app.feature.productivity

import java.security.SecureRandom
import kotlin.random.asKotlinRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** W4: the nonce that lets a pinned shortcut arm a write is random, long, and compared without leaking where it differs. */
class ShortcutNonceTest {

    @Test
    fun aGeneratedNonceIs256BitsOfHexAndNeverRepeats() {
        val random = SecureRandom().asKotlinRandom()
        val nonces = (1..16).map { ShortcutNonce.generate(random) }

        assertTrue(nonces.all { it.length == 64 && it.all { c -> c in "0123456789abcdef" } }, "64 lowercase hex digits: $nonces")
        assertEquals(nonces.size, nonces.toSet().size)
    }

    @Test
    fun onlyAnExactMatchOfANonEmptyNonceIsAccepted() {
        val nonce = ShortcutNonce.generate(SecureRandom().asKotlinRandom())

        assertTrue(ShortcutNonce.matches(nonce, nonce))
        assertTrue(ShortcutNonce.matches(nonce, String(nonce.toCharArray())))
        assertFalse(ShortcutNonce.matches(nonce, nonce.dropLast(1)))
        assertFalse(ShortcutNonce.matches(nonce, nonce + "0"))
        assertFalse(ShortcutNonce.matches(nonce, nonce.uppercase()))
        assertFalse(ShortcutNonce.matches(nonce, ShortcutNonce.generate(SecureRandom().asKotlinRandom())))
    }

    @Test
    fun missingOrEmptyNoncesNeverMatchEvenEachOther() {
        assertFalse(ShortcutNonce.matches(null, null))
        assertFalse(ShortcutNonce.matches("", ""))
        assertFalse(ShortcutNonce.matches(null, "abc"))
        assertFalse(ShortcutNonce.matches("abc", null))
        assertFalse(ShortcutNonce.matches("abc", ""))
    }
}
