package com.tagforge.domain.type2

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeChipSession
import com.tagforge.domain.fakes.FakeType2Chip
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefParseError
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.Type2Tlv
import com.tagforge.domain.read.ReadTagUseCase
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tools.EraseTagUseCase
import com.tagforge.domain.tools.FormatTagUseCase
import com.tagforge.domain.write.WriteNdefUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Read, write, format and erase on the Ultralight family and NTAG210/212: all over raw NfcA, never the platform. */
class UltralightNdefFlowsTest {

    private val uri = NdefMessage(listOf(NdefRecord.uri("https://example.com")))

    private fun userArea(tag: FakeType2Chip) = Bytes.concat((4..tag.chip.userEnd).map(tag::page))

    private suspend fun write(session: FakeChipSession, message: NdefMessage = uri, confirm: Boolean = true) =
        WriteNdefUseCase(message, confirmOverwrite = { confirm }).execute(session) {}

    @Test
    fun writingABlankUltralightInitialisesTheCapabilityContainerAndVerifiesOverRawNfcA() = runTest {
        val tag = FakeType2Chip(Type2Chip.ULTRALIGHT)
        val session = FakeChipSession(tag)
        val result = write(session)
        assertIs<Outcome.Ok<*>>(result, "$result")
        assertEquals(Bytes.of(0xE1, 0x10, 0x06, 0x00), tag.page(3))
        assertEquals(0, session.platformCalls)
        assertTrue(tag.writtenPages.none { it < 3 }, "pages 0-2 must never be written: ${tag.writtenPages}")
    }

    @Test
    fun readingBackWhatWasWrittenNeedsNoTechnologySwitch() = runTest {
        val session = FakeChipSession(FakeType2Chip(Type2Chip.ULTRALIGHT_C))
        write(session)
        val readout = (ReadTagUseCase().execute(session) {} as Outcome.Ok).value
        assertEquals(uri, readout.message)
        assertEquals(Type2Chip.ULTRALIGHT_C, readout.info.type2)
        assertEquals(TagFamily.ULTRALIGHT, readout.info.family)
        assertEquals(0, session.platformCalls)
    }

    @Test
    fun aMessageLargerThanTheCapabilityContainerIsRefusedBeforeAnyWrite() = runTest {
        val tag = FakeType2Chip(Type2Chip.ULTRALIGHT)
        val big = NdefMessage(listOf(NdefRecord.text("x".repeat(60))))
        val result = write(FakeChipSession(tag), big)
        assertIs<TagError.CapacityExceeded>((result as Outcome.Err).error)
        assertEquals(0, tag.writeCount)
    }

    @Test
    fun theWholeFlowWorksWithTheSmallestAllowedFrameLimit() = runTest {
        val tag = FakeType2Chip(Type2Chip.ULTRALIGHT_EV1_21, maxTransceiveLength = 16)
        assertIs<Outcome.Ok<*>>(write(FakeChipSession(tag)))
    }

    @Test
    fun decliningTheOverwriteQuestionWritesNothing() = runTest {
        val tag = FakeType2Chip(Type2Chip.NTAG212)
        val session = FakeChipSession(tag)
        write(session)
        val before = tag.writeCount
        val result = write(session, NdefMessage(listOf(NdefRecord.text("other"))), confirm = false)
        assertEquals(Outcome.Err(TagError.Cancelled), result)
        assertEquals(before, tag.writeCount)
    }

    @Test
    fun aTagPulledAwayMidWriteNeverLeavesATruncatedMessage() = runTest {
        val tag = FakeType2Chip(Type2Chip.ULTRALIGHT_C)
        tag.loseTagAfter(3)
        val result = write(FakeChipSession(tag), NdefMessage(listOf(NdefRecord.text("y".repeat(90)))))
        assertEquals(Outcome.Err(TagError.TagLost), result)
        val unwrapped = Type2Tlv.unwrap(userArea(tag))
        assertFalse(unwrapped is Outcome.Err && unwrapped.error == NdefParseError.Truncated, "$unwrapped")
    }

    @Test
    fun eraseEmptiesTheTagAndNeverTouchesTheFirstFourPages() = runTest {
        val tag = FakeType2Chip(Type2Chip.ULTRALIGHT_EV1_11)
        val session = FakeChipSession(tag)
        write(session)
        val head = (0..3).map(tag::page)
        val erased = EraseTagUseCase().execute(session) {}
        assertTrue((erased as Outcome.Ok).value.hadContent)
        assertEquals(head, (0..3).map(tag::page))
        assertEquals(Bytes.of(0x03, 0x00, 0xFE, 0x00), tag.page(4))
        assertEquals(0, session.platformCalls)
    }

    @Test
    fun eraseKeepsTheNtag212LockControlTlv() = runTest {
        val tag = FakeType2Chip(Type2Chip.NTAG212)
        val session = FakeChipSession(tag)
        write(session)
        EraseTagUseCase().execute(session) {}
        assertEquals(Bytes.of(0x01, 0x03, 0x90, 0x0A, 0x34, 0x03, 0x00, 0xFE), userArea(tag).slice(0, 8))
    }

    @Test
    fun formatBlankUltralightWritesTheCapabilityContainerAndAnEmptyNdefTlv() = runTest {
        val tag = FakeType2Chip(Type2Chip.ULTRALIGHT_C)
        val session = FakeChipSession(tag)
        val result = FormatTagUseCase().execute(session) {}
        assertIs<Outcome.Ok<*>>(result, "$result")
        assertEquals(Bytes.of(0xE1, 0x10, 0x12, 0x00), tag.page(3))
        assertEquals(Bytes.of(0x03, 0x00, 0xFE, 0x00), tag.page(4))
        assertEquals(0, session.platformCalls)
    }
}
