package com.tagforge.domain.tools

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.fakes.FakeGenericNdefSession
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.Type2Tlv
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagTech
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** TT-04 Clone with verify, TT-06 dump/clone round trip, G5 overwrite confirmation. */
class CloneTagUseCaseTest {

    private val message = NdefMessage(listOf(NdefRecord.uri("https://example.com"), NdefRecord.text("hola", "es")))
    private val clone = CloneTagUseCase()

    private val sourceUid = "04 11 22 33 44 55 66".hex()
    private val targetUid = "04 AA BB CC DD EE FF".hex()

    private fun tag(model: NtagModel, uid: Bytes = FakeNtag21x.DEFAULT_UID, content: NdefMessage? = null): FakeNtag21x =
        FakeNtag21x(model, uid).also { t ->
            if (content != null) check(runBlocking { FakeTagSession.ntag(t).writeNdef(content) } is Outcome.Ok)
        }

    private fun readSource(session: TagSession, pages: Boolean = false): TagResult<CloneSource> =
        runBlocking { clone.readSource(includeUserPages = pages).execute(session) {} }

    private fun writeTarget(
        source: CloneSource,
        session: TagSession,
        confirm: suspend (NdefMessage?) -> Boolean = { true },
    ): TagResult<CloneReport> = runBlocking { clone.writeTarget(source, confirm).execute(session) {} }

    private fun sourceOf(tag: FakeNtag21x, pages: Boolean = false): CloneSource =
        (readSource(FakeTagSession.ntag(tag), pages) as Outcome.Ok).value

    private fun outsideUserArea(tag: FakeNtag21x): List<Bytes> =
        tag.snapshot().filterIndexed { page, _ -> page !in tag.model.userStart..tag.model.userEnd }

    private fun userPages(tag: FakeNtag21x) = (tag.model.userStart..tag.model.userEnd).map(tag::page)

    private fun contentOf(tag: FakeNtag21x): Bytes = (Type2Tlv.unwrap(Bytes.concat(userPages(tag))) as Outcome.Ok).value.message

    // ---- NDEF clone ----------------------------------------------------------------------------------------------------

    @Test
    fun cloningAnNtag213MessageToABlankNtag215VerifiesAndLeavesTheirIdentityAlone() {
        val a = tag(NtagModel.NTAG213, sourceUid, message)
        val b = tag(NtagModel.NTAG215, targetUid)
        val untouched = outsideUserArea(b)

        val result = writeTarget(sourceOf(a), FakeTagSession.ntag(b))

        assertIs<Outcome.Ok<CloneReport>>(result)
        assertEquals(DefaultNdefCodec.encode(message), contentOf(b))
        assertEquals(untouched, outsideUserArea(b), "UID, lock bytes, CC, config, PWD and PACK of the destination stay")
        assertEquals(sourceUid, result.value.source.uid)
        assertEquals(targetUid, result.value.target.uid)
        assertFalse(result.value.copiedUserPages)
        assertEquals(DefaultNdefCodec.encode(message).size, result.value.messageSize)
    }

    @Test
    fun theSourceTagIsNeverWritten() {
        val a = tag(NtagModel.NTAG213, sourceUid, message)
        val before = a.snapshot()
        val writes = a.writeCount

        writeTarget(sourceOf(a), FakeTagSession.ntag(tag(NtagModel.NTAG216, targetUid)))

        assertEquals(before, a.snapshot())
        assertEquals(writes, a.writeCount)
    }

    @Test
    fun aMessageTooBigForTheDestinationIsRefusedBeforeAnyWrite() {
        val big = NdefMessage(listOf(NdefRecord.text("x".repeat(290), "en")))   // ~295 bytes
        val a = tag(NtagModel.NTAG215, sourceUid, big)
        val b = tag(NtagModel.NTAG213, targetUid)

        val result = writeTarget(sourceOf(a), FakeTagSession.ntag(b))

        assertIs<Outcome.Err<TagError>>(result)
        assertIs<TagError.CapacityExceeded>(result.error)
        assertEquals(0, b.writeCount)
    }

    @Test
    fun theSourceTagTappedAgainIsRejectedWithZeroWrites() {
        val a = tag(NtagModel.NTAG213, sourceUid, message)
        val source = sourceOf(a)
        val writes = a.writeCount

        val result = writeTarget(source, FakeTagSession.ntag(a))

        assertEquals(Outcome.Err(TagError.SameTag), result)
        assertEquals(writes, a.writeCount)
    }

    @Test
    fun aNonEmptyDestinationAsksFirstAndDecliningWritesNothing() {
        val a = tag(NtagModel.NTAG213, sourceUid, message)
        val old = NdefMessage(listOf(NdefRecord.text("old", "en")))
        val b = tag(NtagModel.NTAG215, targetUid, old)
        val writes = b.writeCount
        var asked: NdefMessage? = null

        val declined = writeTarget(sourceOf(a), FakeTagSession.ntag(b)) { existing -> asked = existing; false }

        assertEquals(Outcome.Err(TagError.Cancelled), declined)
        assertEquals(old, asked, "the dialog can name what would be replaced")
        assertEquals(writes, b.writeCount)

        val accepted = writeTarget(sourceOf(a), FakeTagSession.ntag(b)) { true }
        assertIs<Outcome.Ok<CloneReport>>(accepted)
        assertEquals(DefaultNdefCodec.encode(message), contentOf(b))
    }

    @Test
    fun aBlankDestinationIsNotAskedAnything() {
        var asked = false
        writeTarget(sourceOf(tag(NtagModel.NTAG213, sourceUid, message)), FakeTagSession.ntag(tag(NtagModel.NTAG213, targetUid))) { asked = true; true }

        assertFalse(asked)
    }

    @Test
    fun aLockedDestinationIsReadOnlyWithZeroWrites() {
        val b = tag(NtagModel.NTAG215, targetUid)
        b.setPage(3, "E1 10 3E 0F".hex())

        val result = writeTarget(sourceOf(tag(NtagModel.NTAG213, sourceUid, message)), FakeTagSession.ntag(b))

        assertEquals(Outcome.Err(TagError.ReadOnly), result)
        assertEquals(0, b.writeCount)
    }

    @Test
    fun aGenericDestinationReceivesTheMessageThroughThePlatform() {
        val session = FakeGenericNdefSession()

        val result = writeTarget(sourceOf(tag(NtagModel.NTAG213, sourceUid, message)), session)

        assertIs<Outcome.Ok<CloneReport>>(result)
        assertEquals(listOf(message), session.writes)
    }

    // ---- source problems -------------------------------------------------------------------------------------------------

    @Test
    fun anEmptySourceHasNothingToCopy() {
        assertEquals(Outcome.Err(TagError.NotNdef), readSource(FakeTagSession.ntag(tag(NtagModel.NTAG213, sourceUid))))
    }

    @Test
    fun aProtectedSourceNeedsAuthentication() {
        val a = tag(NtagModel.NTAG213, sourceUid, message)
        a.setPage(NtagModel.NTAG213.cfg0, "00 00 00 04".hex())
        a.setPage(NtagModel.NTAG213.cfg1, "80 00 00 00".hex())

        assertEquals(Outcome.Err(TagError.AuthRequired), readSource(FakeTagSession.ntag(a), pages = true))
    }

    @Test
    fun mifareClassicCannotBeASource() {
        assertEquals(
            Outcome.Err(TagError.Unsupported(TagError.Unsupported.MIFARE_CLASSIC)),
            readSource(FakeTagSession.withoutNtag(TagFamily.MIFARE_CLASSIC)),
        )
    }

    @Test
    fun aTagLostWhileReadingTheSourceIsAFailure() {
        val a = tag(NtagModel.NTAG213, sourceUid, message)
        a.failNext(TagError.TagLost)

        assertEquals(Outcome.Err(TagError.TagLost), readSource(FakeTagSession.ntag(a, identified = false)))
    }

    // ---- optional user pages (same chip model only) ---------------------------------------------------------------------

    @Test
    fun copyingUserPagesReproducesEveryUserPageAndNothingElse() {
        val a = tag(NtagModel.NTAG215, sourceUid, message)
        a.setPage(60, "DE AD BE EF".hex())
        a.setPage(NtagModel.NTAG215.cfg0, "00 00 00 FF".hex())
        a.setPage(NtagModel.NTAG215.pwd, "11 22 33 44".hex())
        a.setPage(2, a.page(2).slice(0, 2) + "00 0F".hex())
        val b = tag(NtagModel.NTAG215, targetUid)
        val untouched = outsideUserArea(b)

        val result = writeTarget(sourceOf(a, pages = true), FakeTagSession.ntag(b))

        assertIs<Outcome.Ok<CloneReport>>(result)
        assertTrue(result.value.copiedUserPages)
        assertEquals(userPages(a), userPages(b))
        assertEquals(untouched, outsideUserArea(b), "pages 0-3, lock bytes, config, PWD and PACK of the destination are never written")
    }

    @Test
    fun dumpCloneDumpRoundTripGivesEqualUserPages() {
        val a = tag(NtagModel.NTAG213, sourceUid, message)
        val b = tag(NtagModel.NTAG213, targetUid)
        val dumpA = (runBlocking { DumpMemoryUseCase().execute(FakeTagSession.ntag(a)) {} } as Outcome.Ok).value.dump

        writeTarget(sourceOf(a, pages = true), FakeTagSession.ntag(b))
        val dumpB = (runBlocking { DumpMemoryUseCase().execute(FakeTagSession.ntag(b)) {} } as Outcome.Ok).value.dump

        val range = NtagModel.NTAG213.userStart..NtagModel.NTAG213.userEnd
        assertEquals(dumpA.pages.slice(range), dumpB.pages.slice(range))
        assertNotEquals(dumpA.pages[0], dumpB.pages[0], "the UID pages differ: this is not a UID clone")
    }

    @Test
    fun userPagesCannotGoToADifferentChipModel() {
        val a = tag(NtagModel.NTAG213, sourceUid, message)
        val b = tag(NtagModel.NTAG215, targetUid)

        val result = writeTarget(sourceOf(a, pages = true), FakeTagSession.ntag(b))

        assertEquals(Outcome.Err(TagError.Unsupported(TagError.Unsupported.USER_PAGES_MODEL)), result)
        assertEquals(0, b.writeCount)
    }

    @Test
    fun copyingPagesToANonEmptyDestinationAsksAndDecliningWritesNothing() {
        val a = tag(NtagModel.NTAG213, sourceUid, message)
        val b = tag(NtagModel.NTAG213, targetUid, NdefMessage(listOf(NdefRecord.text("old", "en"))))
        val writes = b.writeCount

        val result = writeTarget(sourceOf(a, pages = true), FakeTagSession.ntag(b)) { false }

        assertEquals(Outcome.Err(TagError.Cancelled), result)
        assertEquals(writes, b.writeCount)
    }

    @Test
    fun aTagLostDuringAPageCopyIsAFailureAndLeavesOldEmptyOrCompleteContent() {
        val newBytes = DefaultNdefCodec.encode(message)
        val old = NdefMessage(listOf(NdefRecord.text("old", "en")))
        val oldBytes = DefaultNdefCodec.encode(old)
        var completed = false
        for (writesBeforeLoss in 0..16) {
            val a = tag(NtagModel.NTAG213, sourceUid, message)
            val b = tag(NtagModel.NTAG213, targetUid, old)
            b.loseTagAfter(b.writeCount + writesBeforeLoss)

            val result = writeTarget(sourceOf(a, pages = true), FakeTagSession.ntag(b))

            val content = contentOf(b)
            assertTrue(content == newBytes || content == oldBytes || content.isEmpty(), "never half a message (loss after $writesBeforeLoss)")
            when (result) {
                is Outcome.Ok -> {
                    completed = true
                    assertEquals(newBytes, content)
                }
                is Outcome.Err -> assertEquals(TagError.TagLost, result.error, "loss after $writesBeforeLoss")
            }
        }
        assertTrue(completed)
    }

    @Test
    fun aDestinationThatAcknowledgesButIgnoresWritesIsAVerifyMismatch() {
        val a = tag(NtagModel.NTAG213, sourceUid, message)
        val b = tag(NtagModel.NTAG213, targetUid)
        val open = FakeTagSession.ntag(b)
        val ignoring = object : TagSession by open {
            override fun transceiver(tech: TagTech): TagTransceiver? = open.transceiver(tech)?.let { inner ->
                object : TagTransceiver by inner {
                    override suspend fun transceive(command: Bytes): TagResult<Bytes> =
                        if (command[0] == NtagCommands.WRITE) Outcome.Ok(Bytes.of(0x0A)) else inner.transceive(command)
                }
            }
        }

        val result = writeTarget(sourceOf(a, pages = true), ignoring)

        assertIs<Outcome.Err<TagError>>(result)
        assertIs<TagError.VerifyMismatch>(result.error)
    }

    @Test
    fun pageCopyNeedsAnNtagDestination() {
        val a = tag(NtagModel.NTAG213, sourceUid, message)

        val result = writeTarget(sourceOf(a, pages = true), FakeGenericNdefSession())

        assertEquals(Outcome.Err(TagError.Unsupported(TagError.Unsupported.USER_PAGES_MODEL)), result)
    }

    // ---- bookkeeping -----------------------------------------------------------------------------------------------------

    @Test
    fun bothStepsAreCloneOperationsAndTheWriteStepMayWaitForTheUser() {
        val source = sourceOf(tag(NtagModel.NTAG213, sourceUid, message))

        assertEquals(OperationKind.CLONE, clone.readSource().kind)
        assertEquals(OperationKind.CLONE, clone.writeTarget(source) { true }.kind)
        assertEquals(60_000L, clone.writeTarget(source) { true }.timeoutMs)
    }
}
