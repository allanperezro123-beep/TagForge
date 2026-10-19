package com.tagforge.domain.type2

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeChipSession
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.FakeType2Chip
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ntag.Region
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.read.ReadFullTagUseCase
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tools.CloneReport
import com.tagforge.domain.tools.CloneTagUseCase
import com.tagforge.domain.tools.DumpMemoryUseCase
import com.tagforge.domain.write.WriteNdefUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UltralightDumpCloneTest {

    private val message = NdefMessage(listOf(NdefRecord.text("hello tag forge")))

    private suspend fun seed(session: TagSession) {
        check(WriteNdefUseCase(message, confirmOverwrite = { true }).execute(session) {} is Outcome.Ok)
    }

    private suspend fun clone(from: TagSession, to: TagSession, pages: Boolean = false): Outcome<CloneReport, TagError> {
        val clone = CloneTagUseCase()
        val source = (clone.readSource(pages).execute(from) {} as? Outcome.Ok)?.value
            ?: return Outcome.Err(TagError.NotNdef)
        return clone.writeTarget(source) { true }.execute(to) {}
    }

    @Test
    fun plainUltralightIsDumpedWithReadOnlyBecauseItHasNoFastRead() = runTest {
        val tag = FakeType2Chip(Type2Chip.ULTRALIGHT)
        val dump = (DumpMemoryUseCase().execute(FakeChipSession(tag)) {} as Outcome.Ok).value.dump
        assertEquals(16, dump.pages.size)
        assertNull(dump.unreadable)
        assertTrue(tag.commandLog.none { it[0] == NtagCommands.FAST_READ })
        assertEquals(Region.CC, dump.annotations[3].region)
    }

    @Test
    fun ultralightCDumpsAll48PagesAndHidesTheKey() = runTest {
        val tag = FakeType2Chip(Type2Chip.ULTRALIGHT_C)
        tag.setPage(44, Bytes.of(1, 2, 3, 4))
        val dump = (DumpMemoryUseCase().execute(FakeChipSession(tag)) {} as Outcome.Ok).value.dump
        assertEquals(48, dump.pages.size)
        assertTrue(dump.isHiddenByChip(44))
        assertEquals(Bytes.of(0, 0, 0, 0), dump.displayBytes(44))
        assertEquals(Region.USER, dump.annotations[39].region)
    }

    @Test
    fun theLastReadOfA41PageChipDoesNotLeakRolledOverPages() = runTest {
        val tag = FakeType2Chip(Type2Chip.ULTRALIGHT_EV1_21)
        val dump = (DumpMemoryUseCase().execute(FakeChipSession(tag)) {} as Outcome.Ok).value.dump
        assertEquals(41, dump.pages.size)
        assertEquals(Region.PACK, dump.annotations[40].region)
    }

    @Test
    fun theReadScreenScanIncludesTheDumpBecauseItStaysOnRawNfcA() = runTest {
        val session = FakeChipSession(FakeType2Chip(Type2Chip.NTAG212))
        seed(session)
        val scan = (ReadFullTagUseCase().execute(session) {} as Outcome.Ok).value
        assertEquals(message, scan.readout.message)
        assertEquals(41, scan.dump?.pages?.size)
        assertEquals(0, session.platformCalls)
    }

    @Test
    fun anUltralightMessageCanBeClonedToAnNtag215AndBack() = runTest {
        val ultralight = FakeChipSession(FakeType2Chip(Type2Chip.ULTRALIGHT))
        seed(ultralight)
        val ntag = FakeTagSession.ntag(FakeNtag21x(NtagModel.NTAG215, uid = Bytes.of(4, 9, 9, 9, 9, 9, 9)))
        assertIs<Outcome.Ok<*>>(clone(ultralight, ntag))

        val other = FakeChipSession(FakeType2Chip(Type2Chip.ULTRALIGHT_EV1_11, uid = Bytes.of(4, 8, 8, 8, 8, 8, 8)))
        assertIs<Outcome.Ok<*>>(clone(ntag, other))
        assertEquals(0, ultralight.platformCalls + other.platformCalls)
    }

    @Test
    fun cloneRefusesADestinationThatIsTooSmallBeforeWriting() = runTest {
        val big = FakeTagSession.ntag(FakeNtag21x(NtagModel.NTAG215))
        val text = NdefMessage(listOf(NdefRecord.text("z".repeat(100))))
        check(WriteNdefUseCase(text, confirmOverwrite = { true }).execute(big) {} is Outcome.Ok)
        val tag = FakeType2Chip(Type2Chip.ULTRALIGHT)
        val result = clone(big, FakeChipSession(tag))
        assertIs<TagError.CapacityExceeded>((result as Outcome.Err).error)
        assertEquals(0, tag.writeCount)
    }

    @Test
    fun rawUserPagesCopyOnlyBetweenTheSameChipModel() = runTest {
        val source = FakeChipSession(FakeType2Chip(Type2Chip.ULTRALIGHT_EV1_11))
        seed(source)
        val same = FakeType2Chip(Type2Chip.ULTRALIGHT_EV1_11, uid = Bytes.of(4, 7, 7, 7, 7, 7, 7))
        assertIs<Outcome.Ok<*>>(clone(source, FakeChipSession(same), pages = true))
        assertEquals((4..15).map { source.tag.page(it) }, (4..15).map(same::page))

        val different = FakeChipSession(FakeType2Chip(Type2Chip.NTAG210, uid = Bytes.of(4, 6, 6, 6, 6, 6, 6)))
        val refused = clone(source, different, pages = true)
        assertNotNull((refused as? Outcome.Err)?.error as? TagError.Unsupported)
    }
}
