package com.tagforge.domain.tools

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeGenericNdefSession
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.read.ReadNotice
import com.tagforge.domain.read.ReadTagUseCase
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagTech
import com.tagforge.domain.write.WriteNdefUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Any tag Android exposes through Ndef / NdefFormatable works through the platform; everything else is refused up front. */
class GenericNdefFallbackTest {

    private val hello = NdefMessage(listOf(NdefRecord.text("hello", "en")))
    private val infoOnly = TagError.Unsupported(TagError.Unsupported.INFO_ONLY)

    private fun felica() = FakeGenericNdefSession(family = TagFamily.FELICA, techs = setOf(TagTech.NFC_F))

    @Test
    fun aType4TagIsWrittenReadAndErasedThroughThePlatform() = runTest {
        val session = FakeGenericNdefSession(family = TagFamily.ISO14443_4)
        assertIs<Outcome.Ok<*>>(WriteNdefUseCase(hello, confirmOverwrite = { true }).execute(session) {})
        val readout = (ReadTagUseCase().execute(session) {} as Outcome.Ok).value
        assertEquals(hello, readout.message)
        val erased = (EraseTagUseCase().execute(session) {} as Outcome.Ok).value
        assertTrue(erased.hadContent)
        assertNull(session.current)
    }

    @Test
    fun anInfoOnlyFamilyIsRefusedForEveryOperationWithZeroPlatformCalls() = runTest {
        val session = felica()
        assertEquals(Outcome.Err(infoOnly), WriteNdefUseCase(hello, confirmOverwrite = { true }).execute(session) {})
        assertEquals(Outcome.Err(infoOnly), EraseTagUseCase().execute(session) {})
        assertEquals(Outcome.Err(infoOnly), FormatTagUseCase().execute(session) {})
        assertEquals(Outcome.Err(infoOnly), DumpMemoryUseCase().execute(session) {})
        assertEquals(Outcome.Err(infoOnly), CloneTagUseCase().readSource().execute(session) {})
        assertTrue(session.writes.isEmpty())
        assertEquals(0, session.formatCalls)
    }

    @Test
    fun theReadScreenExplainsAnInfoOnlyFamilyInsteadOfSayingNoNdef() = runTest {
        val readout = (ReadTagUseCase().execute(felica()) {} as Outcome.Ok).value
        assertEquals(ReadNotice.UnsupportedFamily(TagFamily.FELICA), readout.notice)
    }

    @Test
    fun isoDepCardsCannotBeDumpedBecauseThatNeedsAuthentication() = runTest {
        val session = FakeGenericNdefSession(family = TagFamily.ISO14443_4)
        val refusal = TagError.Unsupported(TagError.Unsupported.NEEDS_AUTHENTICATION)
        assertEquals(Outcome.Err(refusal), DumpMemoryUseCase().execute(session) {})
    }

    @Test
    fun aBlankFormatableTagIsFormattedAndWrittenThroughNdefFormatable() = runTest {
        val session = FakeGenericNdefSession(formatable = true, family = TagFamily.UNKNOWN, techs = setOf(TagTech.NFC_A, TagTech.NDEF_FORMATABLE))
        assertIs<Outcome.Ok<*>>(FormatTagUseCase().execute(session) {})
        assertEquals(1, session.formatCalls)
    }

    @Test
    fun anNdefTagWithoutNdefFormatableCannotBeFormatted() = runTest {
        val session = FakeGenericNdefSession(family = TagFamily.UNKNOWN, techs = setOf(TagTech.NFC_A, TagTech.NDEF))
        val result = FormatTagUseCase().execute(session) {}
        assertEquals(Outcome.Err(TagError.Unsupported(TagError.Unsupported.FORMAT)), result)
        assertEquals(0, session.formatCalls)
    }

    @Test
    fun aTagWithNoNdefAtAllIsNotNdefForWriting() = runTest {
        val session = FakeGenericNdefSession(family = TagFamily.UNKNOWN, techs = setOf(TagTech.NFC_A))
        assertEquals(Outcome.Err(TagError.NotNdef), WriteNdefUseCase(hello, confirmOverwrite = { true }).execute(session) {})
    }
}
