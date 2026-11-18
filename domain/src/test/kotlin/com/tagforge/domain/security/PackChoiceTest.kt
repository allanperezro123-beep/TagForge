package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.errorOrNull
import com.tagforge.domain.common.getOrNull
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * WU4: a constant PACK is public knowledge and proves nothing, so a blank PACK field means "two random bytes for THIS tag",
 * drawn from an injectable random source. A PACK the user types is still honoured.
 */
class PackChoiceTest {

    @Test
    fun aBlankPackIsTwoRandomBytesFromTheInjectedSource() {
        for (mode in PasswordMode.entries) {
            val chosen = PasswordInput.packOrRandom(mode, "", Random(7)).getOrNull()
            assertEquals(Password.randomPack(Random(7)), chosen, "$mode: the bytes come from the injected source")
            assertEquals(2, chosen!!.size)
        }
    }

    @Test
    fun blankMeansEmptyOrOnlyWhitespace() {
        assertTrue(PasswordInput.packOrRandom(PasswordMode.HEX, "  ", Random(1)) is Outcome.Ok)
    }

    @Test
    fun differentTagsGetDifferentPacksAndNotTheOldConstant() {
        val packs = (0 until 40).map { PasswordInput.packOrRandom(PasswordMode.TEXT, "", Random(it)).getOrNull()!! }.toSet()
        assertTrue(packs.size > 30, "random packs are spread out: ${packs.size} distinct of 40")
    }

    @Test
    fun aTypedPackIsKeptAsTyped() {
        assertEquals(Bytes.of(0x54, 0x46), PasswordInput.packOrRandom(PasswordMode.TEXT, "TF", Random(1)).getOrNull())
        assertEquals(Bytes.of(0xAB, 0xCD), PasswordInput.packOrRandom(PasswordMode.HEX, "ABCD", Random(1)).getOrNull())
    }

    @Test
    fun aTypedPackThatIsWrongIsRefusedNotReplacedByARandomOne() {
        assertEquals(PasswordInputError.WrongLength(2, 1), PasswordInput.packOrRandom(PasswordMode.TEXT, "T", Random(1)).errorOrNull())
        assertEquals(PasswordInputError.NotHex, PasswordInput.packOrRandom(PasswordMode.HEX, "ZZZZ", Random(1)).errorOrNull())
        assertNotEquals(null, PasswordInput.packOrRandom(PasswordMode.HEX, "AB", Random(1)).errorOrNull())
    }
}
