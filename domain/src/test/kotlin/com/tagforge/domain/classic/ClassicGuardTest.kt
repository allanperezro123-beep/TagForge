package com.tagforge.domain.classic

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeMifareClassic
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The sector trailer rule is code: no caller, whatever it asks, can write a trailer or block 0. */
class ClassicGuardTest {

    private val data = Bytes.copyOf(ByteArray(16) { 0x5A })

    private suspend fun openAll(card: FakeMifareClassic, safe: SafeClassic) {
        for (sector in 0 until card.layout.sectorCount) safe.authenticate(sector, FakeMifareClassic.FACTORY, ClassicKeyType.A)
    }

    @Test
    fun noTrailerOfAny1KOr4KCardCanBeWritten() = runTest {
        listOf(ClassicLayout.MINI, ClassicLayout.CLASSIC_1K, ClassicLayout.CLASSIC_4K).forEach { layout ->
            val card = FakeMifareClassic(layout)
            val safe = SafeClassic(card)
            for (sector in 0 until layout.sectorCount) {
                safe.authenticate(sector, FakeMifareClassic.FACTORY, ClassicKeyType.A)
                val refused = safe.writeBlock(layout.trailerOf(sector), data)
                assertEquals(Outcome.Err(TagError.Unsupported(TagError.Unsupported.CLASSIC_TRAILER)), refused, "$layout sector $sector")
            }
            assertEquals(emptyList(), card.writeLog, "the card must never see a trailer write ($layout)")
        }
    }

    @Test
    fun theManufacturerBlockIsNeverWritten() = runTest {
        val card = FakeMifareClassic()
        val safe = SafeClassic(card).also { openAll(card, it) }
        assertEquals(Outcome.Err(TagError.Unsupported(TagError.Unsupported.CLASSIC_MANUFACTURER_BLOCK)), safe.writeBlock(0, data))
        assertTrue(card.writeLog.isEmpty())
    }

    @Test
    fun aDataBlockGoesThroughAndABadSizeOrBlockIsMalformed() = runTest {
        val card = FakeMifareClassic()
        val safe = SafeClassic(card).also { it.authenticate(0, FakeMifareClassic.FACTORY, ClassicKeyType.A) }
        assertIs<Outcome.Ok<*>>(safe.writeBlock(1, data))
        assertEquals(data, card.block(1))
        assertIs<TagError.Malformed>((safe.writeBlock(2, Bytes.of(1, 2, 3)) as Outcome.Err).error)
        assertIs<TagError.Malformed>((safe.writeBlock(64, data) as Outcome.Err).error)
        assertEquals(listOf(1), card.writeLog)
    }

    @Test
    fun theRawChannelNeverLeavesTheGuard() {
        val root = listOf(File("src/main"), File("domain/src/main")).first { it.isDirectory }
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name != "ClassicAccess.kt" && it.name != "TagSession.kt" }
            .filter { Regex("""ClassicChannel|\.classic\(\)""").containsMatchIn(it.readText()) }
            .map { it.name }.toList()
        assertEquals(emptyList(), offenders, "only SafeClassic may touch the raw ClassicChannel")
    }
}
