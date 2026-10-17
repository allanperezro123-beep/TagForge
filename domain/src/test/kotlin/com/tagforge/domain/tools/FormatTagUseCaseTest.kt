package com.tagforge.domain.tools

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.fakes.FakeGenericNdefSession
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.Type2Tlv
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagTech
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TT-02 Format: restores a clean, empty NDEF area (keeping the Lock Control TLV), refuses locked tags before writing. */
class FormatTagUseCaseTest {

    private val uriMessage = NdefMessage(listOf(NdefRecord.uri("https://example.com")))
    private val zero = "00 00 00 00".hex()

    private fun seed(tag: FakeNtag21x, message: NdefMessage = uriMessage) {
        check(runBlocking { FakeTagSession.ntag(tag).writeNdef(message) } is Outcome.Ok)
    }

    private fun format(session: TagSession, phases: MutableList<OperationPhase> = mutableListOf()): TagResult<FormatReport> =
        runBlocking { FormatTagUseCase().execute(session) { phases += it } }

    private fun outsideUserArea(tag: FakeNtag21x): List<Bytes> =
        tag.snapshot().filterIndexed { page, _ -> page !in tag.model.userStart..tag.model.userEnd }

    private fun userPages(tag: FakeNtag21x) = (tag.model.userStart..tag.model.userEnd).map(tag::page)

    private fun zeroUserArea(tag: FakeNtag21x) {
        for (page in tag.model.userStart..tag.model.userEnd) tag.setPage(page, zero)
    }

    private fun assertCleanEmptyArea(tag: FakeNtag21x) {
        val expected = tag.model.factoryNdefArea
        val area = Bytes.concat(userPages(tag))
        assertEquals(expected, area.slice(0, expected.size), "${tag.model}: factory TLV area")
        assertTrue(area.slice(expected.size).toByteArray().all { it == 0.toByte() }, "${tag.model}: the rest of user memory is zero")
    }

    // ---- NTAG21x -----------------------------------------------------------------------------------------------------

    @Test
    fun aTagWithContentIsResetToTheFactoryEmptyAreaOnEveryModel() {
        for (model in NtagModel.entries) {
            val tag = FakeNtag21x(model)
            seed(tag)
            val untouched = outsideUserArea(tag)

            val result = format(FakeTagSession.ntag(tag))

            assertIs<Outcome.Ok<FormatReport>>(result, "$model")
            assertTrue(result.value.changed, "$model")
            assertCleanEmptyArea(tag)
            assertEquals(untouched, outsideUserArea(tag), "$model: pages 0-3, lock, CC, config, PWD and PACK are not touched")
        }
    }

    @Test
    fun ntag213KeepsItsLockControlTlv() {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        seed(tag)

        format(FakeTagSession.ntag(tag))

        assertEquals("01 03 A0 0C".hex(), tag.page(4))
        assertEquals("34 03 00 FE".hex(), tag.page(5))
    }

    @Test
    fun aZeroedTlvAreaGetsTheFactoryTlvBack() {
        val ntag213 = FakeNtag21x(NtagModel.NTAG213)
        zeroUserArea(ntag213)
        val ntag215 = FakeNtag21x(NtagModel.NTAG215)
        zeroUserArea(ntag215)

        assertIs<Outcome.Ok<FormatReport>>(format(FakeTagSession.ntag(ntag213)))
        assertIs<Outcome.Ok<FormatReport>>(format(FakeTagSession.ntag(ntag215)))

        assertEquals("01 03 A0 0C".hex() + "34 03 00 FE".hex(), ntag213.page(4) + ntag213.page(5), "213 gets its Lock Control TLV back")
        assertEquals("03 00 FE 00".hex(), ntag215.page(4))
        assertEquals(Outcome.Ok(null), runBlocking { FakeTagSession.ntag(ntag213).readNdef() }, "and it parses as empty NDEF now")
    }

    @Test
    fun corruptContentIsWipedPageByPage() {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        tag.setPage(4, "FF FF FF FF".hex())
        tag.setPage(5, "12 34 56 78".hex())
        tag.setPage(60, "DE AD BE EF".hex())

        val result = format(FakeTagSession.ntag(tag))

        assertIs<Outcome.Ok<FormatReport>>(result)
        assertCleanEmptyArea(tag)
    }

    @Test
    fun aBlankFactoryTagNeedsNoWrites() {
        for (model in NtagModel.entries) {
            val tag = FakeNtag21x(model)

            val result = format(FakeTagSession.ntag(tag))

            assertIs<Outcome.Ok<FormatReport>>(result, "$model")
            assertFalse(result.value.changed, "$model")
            assertEquals(0, tag.writeCount, "$model")
        }
    }

    @Test
    fun theLengthPageGoesFirstThenTheScrubRunsLowestPageFirst() {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        seed(tag)
        val seedWrites = tag.writtenPages.size

        format(FakeTagSession.ntag(tag))

        val pages = tag.writtenPages.drop(seedWrites)
        assertEquals(5, pages.first())
        assertEquals(pages.drop(1).sorted(), pages.drop(1))
    }

    // ---- locked tags: refused before any write -----------------------------------------------------------------------

    @Test
    fun aTagThatReportsNotWritableIsRefusedBeforeAnyWrite() {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        seed(tag)
        tag.setPage(3, "E1 10 12 0F".hex())
        val writes = tag.writeCount

        assertEquals(Outcome.Err(TagError.ReadOnly), format(FakeTagSession.ntag(tag)))
        assertEquals(writes, tag.writeCount)
    }

    @Test
    fun aCcThatSaysReadOnlyIsHonouredEvenWithStaleInfo() {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        seed(tag)
        val open = FakeTagSession.ntag(tag)
        val staleInfo = open.info
        tag.setPage(3, "E1 10 3E 0F".hex())
        val stale = object : TagSession by open {
            override val info = staleInfo
        }
        val writes = tag.writeCount

        assertEquals(Outcome.Err(TagError.ReadOnly), format(stale))
        assertEquals(writes, tag.writeCount)
    }

    @Test
    fun aLockBitOnTheLengthPageIsReadOnlyAndNothingChanges() {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        seed(tag)
        tag.setPage(2, tag.page(2).slice(0, 2) + "20 00".hex())
        val before = userPages(tag)

        assertEquals(Outcome.Err(TagError.ReadOnly), format(FakeTagSession.ntag(tag)))
        assertEquals(before, userPages(tag))
    }

    // ---- tag lost -----------------------------------------------------------------------------------------------------

    @Test
    fun aTagLostAtAnyPointIsNeverSuccessAndNeverLeavesAHalfMessage() {
        val oldBytes = com.tagforge.domain.ndef.DefaultNdefCodec.encode(uriMessage)
        var completed = false
        for (writesBeforeLoss in 0..8) {
            val tag = FakeNtag21x(NtagModel.NTAG213)
            seed(tag)
            tag.loseTagAfter(tag.writeCount + writesBeforeLoss)

            val result = format(FakeTagSession.ntag(tag))

            val area = (Type2Tlv.unwrap(Bytes.concat(userPages(tag))) as Outcome.Ok).value
            assertEquals(5, area.leadingBytes, "loss after $writesBeforeLoss")
            assertTrue(area.message == oldBytes || area.message.isEmpty(), "old or empty (loss after $writesBeforeLoss)")
            when (result) {
                is Outcome.Ok -> {
                    completed = true
                    assertCleanEmptyArea(tag)
                }
                is Outcome.Err -> assertEquals(TagError.TagLost, result.error, "loss after $writesBeforeLoss")
            }
        }
        assertTrue(completed)
    }

    @Test
    fun tagLostWhileConnectingIsAFailure() {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        tag.failNext(TagError.TagLost)

        assertEquals(Outcome.Err(TagError.TagLost), format(FakeTagSession.ntag(tag, identified = false)))
    }

    @Test
    fun aTagThatAcknowledgesButIgnoresWritesIsAVerifyMismatch() {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        seed(tag)
        val open = FakeTagSession.ntag(tag)
        val ignoring = object : TagSession by open {
            override fun transceiver(tech: TagTech): TagTransceiver? = open.transceiver(tech)?.let { inner ->
                object : TagTransceiver by inner {
                    override suspend fun transceive(command: Bytes): TagResult<Bytes> =
                        if (command[0] == NtagCommands.WRITE) Outcome.Ok(Bytes.of(0x0A)) else inner.transceive(command)
                }
            }
        }

        val result = format(ignoring)

        assertIs<Outcome.Err<TagError>>(result)
        assertIs<TagError.VerifyMismatch>(result.error)
    }

    // ---- through the platform (NdefFormatable) -----------------------------------------------------------------------

    @Test
    fun anUnformattedTagIsFormattedByThePlatformAndEndsUpEmpty() {
        val session = FakeGenericNdefSession(formatable = true, techs = setOf(TagTech.NFC_A, TagTech.NDEF_FORMATABLE), family = TagFamily.ULTRALIGHT)

        val result = format(session)

        assertIs<Outcome.Ok<FormatReport>>(result)
        assertEquals(1, session.formatCalls)
        assertTrue(session.formatted)
        assertNull((runBlocking { session.readNdef() } as Outcome.Ok).value)
    }

    @Test
    fun anNtagWithoutACapabilityContainerFallsBackToThePlatformFormat() {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        seed(tag)
        tag.setPage(3, zero)
        val open = FakeTagSession.ntag(tag)
        var formatCalls = 0
        val counting = object : TagSession by open {
            override val info = open.info.copy(techs = open.info.techs - TagTech.NDEF + TagTech.NDEF_FORMATABLE, ndef = null)
            override suspend fun identify(): TagResult<com.tagforge.domain.tag.TagInfo> = Outcome.Ok(info)
            override suspend fun formatNdef(initial: NdefMessage?): TagResult<Unit> {
                formatCalls++
                return open.formatNdef(initial)
            }
        }

        val result = format(counting)

        assertIs<Outcome.Ok<FormatReport>>(result, "$result")
        assertEquals(1, formatCalls)
        assertEquals("03 00 FE 00".hex(), tag.page(4), "the platform wrote the empty NDEF TLV over the old message")
        assertEquals("00 00 00 00".hex(), tag.page(3), "the CC page is never touched by Tag Forge itself")
    }

    @Test
    fun aFormatThatTheTagRefusesIsReportedAsUnsupported() {
        val session = FakeGenericNdefSession(message = uriMessage, formatable = false)

        assertEquals(Outcome.Err(TagError.Unsupported("format")), format(session))
        assertEquals(0, session.formatCalls)
    }

    @Test
    fun aLockedFormatableTagIsRefusedBeforeFormatting() {
        val session = FakeGenericNdefSession(formatable = true, writable = false)

        assertEquals(Outcome.Err(TagError.ReadOnly), format(session))
        assertEquals(0, session.formatCalls)
    }

    @Test
    fun mifareClassicWithNothingToFormatWithIsExplained() {
        val result = format(FakeTagSession.withoutNtag(TagFamily.MIFARE_CLASSIC))

        assertEquals(Outcome.Err(TagError.Unsupported(TagError.Unsupported.MIFARE_CLASSIC)), result)
    }

    // ---- bookkeeping -----------------------------------------------------------------------------------------------

    @Test
    fun reportsPhasesTheFormatKindAndAGenerousTimeout() {
        val phases = mutableListOf<OperationPhase>()
        val tag = FakeNtag21x(NtagModel.NTAG216)
        seed(tag)

        format(FakeTagSession.ntag(tag), phases)

        assertEquals(
            listOf(OperationPhase.CONNECTING, OperationPhase.READING, OperationPhase.WRITING, OperationPhase.VERIFYING),
            phases,
        )
        val operation = FormatTagUseCase()
        assertEquals(OperationKind.FORMAT, operation.kind)
        assertTrue(operation.timeoutMs!! > ScanCoordinator.DEFAULT_TIMEOUT_MS, "a 222-page wipe can outlast the 10 s default")
    }
}
