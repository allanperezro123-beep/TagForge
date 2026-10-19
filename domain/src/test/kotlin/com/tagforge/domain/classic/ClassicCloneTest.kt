package com.tagforge.domain.classic

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeClassicSession
import com.tagforge.domain.fakes.FakeMifareClassic
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tools.CloneReport
import com.tagforge.domain.tools.CloneTagUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** MIFARE Classic clone: readable data blocks only, never a trailer, never block 0, verified by reading back. */
class ClassicCloneTest {

    private val random = Bytes.of(0x13, 0x37, 0xC0, 0xDE, 0xBA, 0xBE)
    private fun filled(value: Int) = Bytes.copyOf(ByteArray(16) { value.toByte() })

    private fun source(): FakeClassicSession {
        val card = FakeMifareClassic(uid = Bytes.of(1, 2, 3, 4))
        (0 until 16).forEach { sector ->
            for (block in card.layout.writableBlocksOf(sector)) card.setBlock(block, filled(sector * 4 + (block % 4) + 1))
        }
        return FakeClassicSession(card)
    }

    private fun target(layout: ClassicLayout = ClassicLayout.CLASSIC_1K) = FakeClassicSession(FakeMifareClassic(layout, uid = Bytes.of(9, 8, 7, 6)))

    private suspend fun clone(from: FakeClassicSession, to: FakeClassicSession, confirm: Boolean = true): Outcome<CloneReport, TagError> {
        val clone = CloneTagUseCase()
        val captured = when (val read = clone.readSource().execute(from) {}) {
            is Outcome.Err -> return read
            is Outcome.Ok -> read.value
        }
        return clone.writeTarget(captured) { confirm }.execute(to) {}
    }

    @Test
    fun everyReadableDataBlockIsCopiedAndVerified() = runTest {
        val from = source()
        val to = target()
        val report = (clone(from, to) as Outcome.Ok).value

        val expected = (0 until 16).sumOf { from.card.layout.writableBlocksOf(it).count() }
        assertEquals(expected, report.classicBlocksCopied)
        assertEquals(emptyList(), report.classicSectorsSkipped)
        for (sector in 0 until 16) for (block in from.card.layout.writableBlocksOf(sector)) {
            assertEquals(from.card.block(block), to.card.block(block), "block $block")
        }
        assertEquals(0, from.platformCalls + to.platformCalls)
    }

    @Test
    fun noTrailerAndNoBlockZeroIsEverWrittenAndTheKeysOfTheDestinationSurvive() = runTest {
        val from = source().also { it.card.setKeys(1, random, FakeMifareClassic.FACTORY) }
        val to = target()
        val trailersBefore = (0 until 16).map { to.card.block(to.card.layout.trailerOf(it)) }
        val blockZeroBefore = to.card.block(0)

        clone(from, to)

        assertEquals(emptyList(), to.card.trailerWrites)
        assertTrue(to.card.writeLog.none { it == 0 })
        assertEquals(trailersBefore, (0 until 16).map { to.card.block(to.card.layout.trailerOf(it)) })
        assertEquals(blockZeroBefore, to.card.block(0))
        assertEquals(emptyList(), from.card.writeLog, "the source is never written")
    }

    @Test
    fun aLockedSourceSectorIsNotCopiedAndALockedDestinationSectorIsReportedSkipped() = runTest {
        val from = source().also { it.card.setKeys(2, random, random) }
        val to = target().also { it.card.setKeys(5, random, random) }
        val report = (clone(from, to) as Outcome.Ok).value
        assertEquals(listOf(5), report.classicSectorsSkipped)
        assertTrue(to.card.writeLog.none { to.card.layout.sectorOf(it) == 5 || to.card.layout.sectorOf(it) == 2 })
    }

    @Test
    fun aDestinationWithAnotherLayoutIsRefusedBeforeAnyWrite() = runTest {
        val to = target(ClassicLayout.CLASSIC_4K)
        val result = clone(source(), to)
        assertEquals(Outcome.Err(TagError.Unsupported(TagError.Unsupported.USER_PAGES_MODEL)), result)
        assertEquals(emptyList(), to.card.writeLog)
    }

    @Test
    fun decliningTheOverwriteQuestionWritesNothing() = runTest {
        val to = target()
        assertEquals(Outcome.Err(TagError.Cancelled), clone(source(), to, confirm = false))
        assertEquals(emptyList(), to.card.writeLog)
    }

    @Test
    fun theSameCardTwiceIsRefused() = runTest {
        val from = source()
        assertEquals(Outcome.Err(TagError.SameTag), clone(from, from))
    }

    @Test
    fun aSourceWhoseEverySectorNeedsAPrivateKeyHasNothingToCopy() = runTest {
        val locked = FakeMifareClassic().also { c -> (0 until 16).forEach { c.setKeys(it, random, random) } }
        assertEquals(Outcome.Err(TagError.AuthRequired), clone(FakeClassicSession(locked), target()))
    }

    @Test
    fun aDestinationThatCannotBeOpenedAtAllIsAuthRequiredAndUntouched() = runTest {
        val locked = FakeMifareClassic(uid = Bytes.of(9, 8, 7, 6)).also { c -> (0 until 16).forEach { c.setKeys(it, random, random) } }
        val result = clone(source(), FakeClassicSession(locked))
        assertEquals(Outcome.Err(TagError.AuthRequired), result)
        assertEquals(emptyList(), locked.writeLog)
    }

    @Test
    fun aTagPulledAwayMidCloneIsTagLostNeverSuccess() = runTest {
        val to = target()
        val captured = (CloneTagUseCase().readSource().execute(source()) {} as Outcome.Ok).value
        to.card.loseTag()
        val result = CloneTagUseCase().writeTarget(captured) { true }.execute(to) {}
        assertIs<Outcome.Err<*>>(result)
        assertEquals(TagError.TagLost, result.error)
    }
}
