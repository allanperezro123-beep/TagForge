package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.errorOrNull
import com.tagforge.domain.common.getOrNull
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * WU4: PWD = HMAC-SHA256(passphrase, "TagForge-NTAG-PWD-v1" || UID)[0..3], PACK = [4..5]. Every tag of a fleet gets its own
 * password and PACK from one master passphrase, so a cracked tag does not open the others.
 */
class DiversifiedPasswordTest {

    private val uid = Bytes.of(0x04, 0xA1, 0xB2, 0xC3, 0xD4, 0xE5, 0xF6)
    private val phrase = "correct horse battery staple"

    /** Independent reference: javax.crypto written out in the test, not shared with the implementation. */
    private fun reference(passphrase: String, uid: Bytes): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(passphrase.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        mac.update("TagForge-NTAG-PWD-v1".toByteArray(Charsets.US_ASCII))
        return mac.doFinal(uid.toByteArray())
    }

    @Test
    fun matchesAFixedKnownVector() {
        val (password, pack) = DiversifiedPassword.derive(phrase, uid)

        assertEquals("37D9F016", password.hex)
        assertEquals(Bytes.of(0x00, 0xCC), pack)
    }

    @Test
    fun matchesTheIndependentHmacSha256Reference() {
        for (text in listOf(phrase, "another long passphrase 42!", "contraseña-segura-2026")) {
            val expected = reference(text, uid)
            val (password, pack) = DiversifiedPassword.derive(text, uid)

            assertEquals(Bytes.copyOf(expected.copyOfRange(0, 4)), password.bytes, text)
            assertEquals(Bytes.copyOf(expected.copyOfRange(4, 6)), pack, text)
        }
    }

    @Test
    fun anotherUidGivesAnotherPasswordAndPack() {
        val other = Bytes.of(0x04, 0xA1, 0xB2, 0xC3, 0xD4, 0xE5, 0xF7)
        val (password, pack) = DiversifiedPassword.derive(phrase, other)

        assertEquals("6781A49D", password.hex)
        assertEquals(Bytes.of(0x92, 0xAF), pack)
        assertNotEquals(DiversifiedPassword.derive(phrase, uid).first, password)
    }

    @Test
    fun anotherPassphraseGivesAnotherPassword() {
        assertNotEquals(DiversifiedPassword.derive(phrase, uid).first, DiversifiedPassword.derive("$phrase!", uid).first)
    }

    @Test
    fun aPassphraseWithAccentsIsTheSameWhateverWayTheKeyboardComposedIt() {
        val composed = "contraseña-segura-2026"
        val decomposed = "contraseña-segura-2026"

        assertEquals(DiversifiedPassword.derive(composed, uid), DiversifiedPassword.derive(decomposed, uid))
    }

    @Test
    fun theDerivedPasswordIsNeverTheFactoryValueAndTheFallbackIsDeterministic() {
        val factory = ByteArray(32) { 0xFF.toByte() }
        val next = ByteArray(32) { it.toByte() }

        val picked = DiversifiedPassword.pick(sequenceOf(factory, next))

        assertEquals(Bytes.of(0x00, 0x01, 0x02, 0x03), picked.first.bytes)
        assertEquals(Bytes.of(0x04, 0x05), picked.second)
    }

    @Test
    fun anEmptyPassphraseIsRefusedBeforeDerivingAnything() {
        assertTrue(runCatching { DiversifiedPassword.derive("", uid) }.isFailure)
    }

    @Test
    fun theDerivedPairNeverPrintsItsValues() {
        val text = DiversifiedPassword.derive(phrase, uid).first.toString()

        assertEquals("Password(****)", text)
    }

    // ---- the passphrase gate ----

    @Test
    fun aGoodPassphraseIsAccepted() {
        assertNull(DiversifiedPassword.check(phrase))
        assertNull(DiversifiedPassword.check("Tr0ub4dor&3-xylophone"))
        assertEquals("37D9F016", DiversifiedPassword.deriveChecked(phrase, uid).getOrNull()!!.first.hex)
    }

    @Test
    fun aPassphraseShorterThanTwelveCharactersIsRefused() {
        assertEquals(12, DiversifiedPassword.MIN_PASSPHRASE_LENGTH)
        assertEquals(PassphraseProblem.TooShort(minimum = 12, actual = 11), DiversifiedPassword.check("abcXYZ12-9!"))
        assertEquals(PassphraseProblem.TooShort(minimum = 12, actual = 0), DiversifiedPassword.check(""))
        assertNull(DiversifiedPassword.check("abcXYZ12-9!q"))
    }

    @Test
    fun obviouslyWeakPassphrasesAreRefusedEvenWhenLongEnough() {
        assertEquals(PassphraseProblem.TooFewDistinctCharacters, DiversifiedPassword.check("aaaaaaaaaaaaaaaa"))
        assertEquals(PassphraseProblem.Repetitive, DiversifiedPassword.check("abcabcabcabcabc"))
        assertEquals(PassphraseProblem.Repetitive, DiversifiedPassword.check("ab12ab12ab12ab12"))
        assertEquals(PassphraseProblem.Sequential, DiversifiedPassword.check("abcdefghijklmnop"))
        assertEquals(PassphraseProblem.Sequential, DiversifiedPassword.check("1234567890123"))
        assertEquals(PassphraseProblem.Common, DiversifiedPassword.check("passwordpassword"))
        assertEquals(PassphraseProblem.Common, DiversifiedPassword.check("Qwertyuiop1234"))
    }

    @Test
    fun deriveCheckedNeverDerivesFromARefusedPassphrase() {
        val result = DiversifiedPassword.deriveChecked("short", uid)

        assertTrue(result is Outcome.Err)
        assertEquals(PassphraseProblem.TooShort(12, 5), result.errorOrNull())
    }
}
