package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.errorOrNull
import com.tagforge.domain.common.getOrNull
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** UX-A1: a password is typed as ordinary text (4 ASCII characters = 4 bytes); hexadecimal is the advanced alternative. */
class PasswordInputTest {

    private fun text(input: String) = PasswordInput.parse(PasswordMode.TEXT, SecretKind.PASSWORD, input)
    private fun hex(input: String) = PasswordInput.parse(PasswordMode.HEX, SecretKind.PASSWORD, input)

    @Test
    fun fourAsciiCharactersAreFourBytes() {
        assertEquals(Bytes.of(0x61, 0x62, 0x63, 0x64), text("abcd").getOrNull())
        assertEquals(Bytes.of(0x41, 0x31, 0x21, 0x7E), text("A1!~").getOrNull())
    }

    @Test
    fun textMustBeExactlyFourCharacters() {
        assertEquals(PasswordInputError.Empty, text("").errorOrNull())
        assertEquals(PasswordInputError.WrongLength(expected = 4, actual = 3), text("abc").errorOrNull())
        assertEquals(PasswordInputError.WrongLength(expected = 4, actual = 5), text("abcde").errorOrNull())
    }

    @Test
    fun textRejectsNonAsciiAndSpacesWithTypedErrors() {
        assertEquals(PasswordInputError.NonAscii, text("año1").errorOrNull())
        assertEquals(PasswordInputError.NonAscii, text("abé1").errorOrNull())
        assertEquals(PasswordInputError.NonAscii, text("ab€1").errorOrNull())
        assertEquals(PasswordInputError.HasSpace, text("ab d").errorOrNull())
    }

    @Test
    fun hexAcceptsGroupedDigitsAndExactlyEightOfThem() {
        val expected = Bytes.of(0x12, 0x34, 0x56, 0x78)
        assertEquals(expected, hex("12345678").getOrNull())
        assertEquals(expected, hex("12 34 56 78").getOrNull())
        assertEquals(expected, hex("12:34:56:78").getOrNull())
        assertEquals(Bytes.of(0xAB, 0xCD, 0xEF, 0x01), hex("abcdef01").getOrNull())
    }

    @Test
    fun hexErrorsAreTyped() {
        assertEquals(PasswordInputError.Empty, hex(" ").errorOrNull())
        assertEquals(PasswordInputError.WrongLength(expected = 8, actual = 7), hex("1234567").errorOrNull())
        assertEquals(PasswordInputError.NotHex, hex("1234567G").errorOrNull())
    }

    @Test
    fun theFactoryValueIsRefusedInHex() {
        assertEquals(PasswordInputError.FactoryValue, PasswordInput.password(PasswordMode.HEX, "FFFFFFFF").errorOrNull())
        assertTrue(PasswordInput.password(PasswordMode.HEX, "FFFFFFFE") is Outcome.Ok)
        assertEquals(Bytes.of(0x61, 0x62, 0x63, 0x64), (PasswordInput.password(PasswordMode.TEXT, "abcd") as Outcome.Ok).value.bytes)
    }

    @Test
    fun packIsTwoCharactersOrFourHexDigits() {
        assertEquals(Bytes.of(0x54, 0x46), PasswordInput.parse(PasswordMode.TEXT, SecretKind.PACK, "TF").getOrNull())
        assertEquals(Bytes.of(0xAB, 0xCD), PasswordInput.parse(PasswordMode.HEX, SecretKind.PACK, "AB CD").getOrNull())
        assertEquals(
            PasswordInputError.WrongLength(expected = 2, actual = 3),
            PasswordInput.parse(PasswordMode.TEXT, SecretKind.PACK, "TFX").errorOrNull(),
        )
    }

    @Test
    fun capacityAndLiveCounterFollowTheMode() {
        assertEquals(4, PasswordInput.capacity(PasswordMode.TEXT, SecretKind.PASSWORD))
        assertEquals(8, PasswordInput.capacity(PasswordMode.HEX, SecretKind.PASSWORD))
        assertEquals(2, PasswordInput.capacity(PasswordMode.TEXT, SecretKind.PACK))
        assertEquals(4, PasswordInput.capacity(PasswordMode.HEX, SecretKind.PACK))
        assertEquals(3, PasswordInput.count(PasswordMode.TEXT, "abc"))
        assertEquals(4, PasswordInput.count(PasswordMode.HEX, "12 34"))
    }

    @Test
    fun typingIsCappedAndHexIsNormalised() {
        assertEquals("abcd", PasswordInput.sanitize(PasswordMode.TEXT, SecretKind.PASSWORD, "abcdef"))
        assertEquals("añ", PasswordInput.sanitize(PasswordMode.TEXT, SecretKind.PACK, "añb"), "non-ASCII stays visible so it can be explained")
        assertEquals("12AB34CD", PasswordInput.sanitize(PasswordMode.HEX, SecretKind.PASSWORD, "12 ab:34-cd99"))
        assertEquals("12G4", PasswordInput.sanitize(PasswordMode.HEX, SecretKind.PACK, "12g4x"), "a wrong letter stays visible so it can be explained")
    }

    @Test
    fun hexIsGroupedByByte() {
        assertEquals("12 34 56 78", PasswordInput.groupHex("12345678"))
        assertEquals("12 34 5", PasswordInput.groupHex("12345"))
        assertEquals("", PasswordInput.groupHex(""))
    }

    @Test
    fun switchingModeKeepsTheValueWhenItCanBeRepresented() {
        assertEquals("61626364", PasswordInput.convert(PasswordMode.TEXT, PasswordMode.HEX, "abcd"))
        assertEquals("6162", PasswordInput.convert(PasswordMode.TEXT, PasswordMode.HEX, "ab"))
        assertEquals("abcd", PasswordInput.convert(PasswordMode.HEX, PasswordMode.TEXT, "61626364"))
        assertEquals("", PasswordInput.convert(PasswordMode.HEX, PasswordMode.TEXT, "00000001"), "non-printable bytes have no text form")
        assertEquals("", PasswordInput.convert(PasswordMode.TEXT, PasswordMode.HEX, "añb"))
        assertEquals("abcd", PasswordInput.convert(PasswordMode.TEXT, PasswordMode.TEXT, "abcd"))
    }

    @Test
    fun renderShowsBytesInTheChosenMode() {
        assertEquals("abcd", PasswordInput.render(PasswordMode.TEXT, Bytes.of(0x61, 0x62, 0x63, 0x64)))
        assertNull(PasswordInput.render(PasswordMode.TEXT, Bytes.of(0, 0, 0, 1)))
        assertEquals("00000001", PasswordInput.render(PasswordMode.HEX, Bytes.of(0, 0, 0, 1)))
    }

    @Test
    fun generatedSecretsAlwaysParseAndAreRepeatableWithASeed() {
        for (mode in PasswordMode.entries) {
            repeat(50) { seed ->
                val random = Random(seed)
                assertTrue(PasswordInput.password(mode, PasswordInput.generate(mode, random)) is Outcome.Ok, "$mode seed $seed")
                assertTrue(PasswordInput.parse(mode, SecretKind.PACK, PasswordInput.generatePack(mode, random)) is Outcome.Ok)
            }
            assertEquals(PasswordInput.generate(mode, Random(9)), PasswordInput.generate(mode, Random(9)))
        }
        assertEquals(4, PasswordInput.generate(PasswordMode.TEXT, Random(1)).length)
        assertEquals(8, PasswordInput.generate(PasswordMode.HEX, Random(1)).length)
        assertTrue(PasswordInput.generate(PasswordMode.TEXT, Random(1)).none { it in "0O1lI" }, "no look-alike characters")
    }

    @Test
    fun theDefaultPackIsTheReadableTfAndParses() {
        assertEquals("TF", PasswordInput.defaultPack(PasswordMode.TEXT))
        assertEquals("5446", PasswordInput.defaultPack(PasswordMode.HEX))
        assertEquals(Bytes.of(0x54, 0x46), PasswordInput.parse(PasswordMode.TEXT, SecretKind.PACK, "TF").getOrNull())
    }
}
