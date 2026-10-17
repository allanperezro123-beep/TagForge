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

/** TT-01 Erase NDEF: the empty TLV goes in, the Lock Control TLV and everything outside the NDEF area stays. */
class EraseTagUseCaseTest {

    private val uriMessage = NdefMessage(listOf(NdefRecord.uri("https://example.com")))   // 16 bytes

    private fun seed(tag: FakeNtag21x, message: NdefMessage = uriMessage) {
        check(runBlocking { FakeTagSession.ntag(tag).writeNdef(message) } is Outcome.Ok)
    }

    private fun erase(session: TagSession, phases: MutableList<OperationPhase> = mutableListOf()): TagResult<EraseReport> =
        runBlocking { EraseTagUseCase().execute(session) { phases += it } }

    /** Everything that is NOT user memory: pages 0-3, dynamic lock, CFG0/1, PWD, PACK. Must never change in P3. */
    private fun outsideUserArea(tag: FakeNtag21x): List<Bytes> =
        tag.snapshot().filterIndexed { page, _ -> page !in tag.model.userStart..tag.model.userEnd }

    private fun userPages(tag: FakeNtag21x) = (tag.model.userStart..tag.model.userEnd).map(tag::page)

    private fun parsed(tag: FakeNtag21x): Type2Tlv.Area {
        val area = Bytes.concat(userPages(tag))
        return (Type2Tlv.unwrap(area) as Outcome.Ok).value
    }

    // ---- the result on a real-looking NTAG ----------------------------------------------------------------------

    @Test
    fun ntag213KeepsTheLockControlTlvAndReadsAsAnEmptyNdef() {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        seed(tag)
        val untouched = outsideUserArea(tag)
        val seedWrites = tag.writtenPages.size

        val session = FakeTagSession.ntag(tag)
        val result = erase(session)

        assertIs<Outcome.Ok<EraseReport>>(result)
        assertTrue(result.value.hadContent)
        assertEquals("01 03 A0 0C".hex(), tag.page(4))
        assertEquals("34 03 00 FE".hex(), tag.page(5))
        assertEquals("E1 10 12 00".hex(), tag.page(3), "CC unchanged")
        assertEquals(untouched, outsideUserArea(tag), "pages 0-3, lock bytes and config pages were not touched")
        assertEquals(Outcome.Ok(null), runBlocking { session.readNdef() }, "the tag parses as an empty NDEF")
        assertTrue(userPages(tag).drop(2).all { it == "00 00 00 00".hex() }, "the old message is scrubbed, not just hidden")
        assertFalse(4 in tag.writtenPages.drop(seedWrites), "page 4 holds the Lock Control TLV and is not rewritten")
    }

    @Test
    fun ntag215StartsWithTheEmptyTlvAndZeros() {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        seed(tag)
        val untouched = outsideUserArea(tag)

        val result = erase(FakeTagSession.ntag(tag))

        assertIs<Outcome.Ok<EraseReport>>(result)
        assertEquals("03 00 FE 00".hex(), tag.page(4))
        assertTrue(userPages(tag).drop(1).all { it == "00 00 00 00".hex() })
        assertEquals(untouched, outsideUserArea(tag))
    }

    @Test
    fun theLengthPageIsWrittenFirstSoAnInterruptionLeavesAnEmptyTag() {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        seed(tag)
        val seedWrites = tag.writtenPages.size

        erase(FakeTagSession.ntag(tag))

        assertEquals(5, tag.writtenPages.drop(seedWrites).first(), "page 5 holds `34 03 <len>`: it is cleared before any scrubbing")
    }

    @Test
    fun aLongMessageWithAThreeByteLengthIsScrubbedCompletely() {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        seed(tag, NdefMessage(listOf(NdefRecord.text("x".repeat(300), "en"))))
        assertEquals("03 FF".hex(), tag.page(4).slice(0, 2), "setup: a 3-byte length header")

        val result = erase(FakeTagSession.ntag(tag))

        assertIs<Outcome.Ok<EraseReport>>(result)
        assertEquals("03 00 FE 00".hex(), tag.page(4))
        assertTrue(userPages(tag).drop(1).all { it == "00 00 00 00".hex() }, "300 bytes of old text are gone")
    }

    @Test
    fun anyLeadingTlvIsPreservedNotJustTheNtag213Factory() {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        tag.setPage(4, "01 03 A0 0C".hex())
        tag.setPage(5, "34 03 00 FE".hex())
        seed(tag)

        val result = erase(FakeTagSession.ntag(tag))

        assertIs<Outcome.Ok<EraseReport>>(result)
        assertEquals("01 03 A0 0C".hex(), tag.page(4))
        assertEquals("34 03 00 FE".hex(), tag.page(5))
        assertEquals(5, parsed(tag).leadingBytes)
    }

    // ---- nothing to do ---------------------------------------------------------------------------------------------

    @Test
    fun anAlreadyEmptyTagSucceedsWithZeroWrites() {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        val result = erase(FakeTagSession.ntag(tag))

        assertIs<Outcome.Ok<EraseReport>>(result)
        assertFalse(result.value.hadContent)
        assertEquals(0, tag.writeCount)
    }

    @Test
    fun memoryWithNoNdefStructureAtAllIsNotNdefAndIsLeftAlone() {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        tag.setPage(4, "00 00 00 00".hex())

        val result = erase(FakeTagSession.ntag(tag))

        assertEquals(Outcome.Err(TagError.NotNdef), result, "that is a job for Format")
        assertEquals(0, tag.writeCount)
    }

    // ---- locked tags -----------------------------------------------------------------------------------------------

    @Test
    fun aTagThatReportsNotWritableIsReadOnlyWithZeroWrites() {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        seed(tag)
        tag.setPage(3, "E1 10 12 0F".hex())
        val writesBefore = tag.writeCount

        val result = erase(FakeTagSession.ntag(tag))

        assertEquals(Outcome.Err(TagError.ReadOnly), result)
        assertEquals(writesBefore, tag.writeCount)
    }

    @Test
    fun aCcThatSaysReadOnlyIsHonouredEvenIfTheOsInfoWasStale() {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        seed(tag)
        val open = FakeTagSession.ntag(tag)
        val staleInfo = open.info
        tag.setPage(3, "E1 10 12 0F".hex())
        val stale = object : TagSession by open {
            override val info = staleInfo
        }
        val writesBefore = tag.writeCount

        val result = erase(stale)

        assertEquals(Outcome.Err(TagError.ReadOnly), result)
        assertEquals(writesBefore, tag.writeCount, "the CC check happens before the first write")
    }

    @Test
    fun aPageLockedThroughTheLockBitsRefusesTheFirstWriteAndMeansReadOnly() {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        seed(tag)
        tag.setPage(2, tag.page(2).slice(0, 2) + "20 00".hex())   // L5: the page that holds the NDEF length
        val before = userPages(tag)

        val result = erase(FakeTagSession.ntag(tag))

        assertEquals(Outcome.Err(TagError.ReadOnly), result)
        assertEquals(before, userPages(tag), "nothing changed")
    }

    // ---- tag lost --------------------------------------------------------------------------------------------------

    @Test
    fun aTagLostAtAnyPointIsAFailureAndLeavesAParsableTag() {
        val oldBytes = com.tagforge.domain.ndef.DefaultNdefCodec.encode(uriMessage)
        var sawErase = false
        for (writesBeforeLoss in 0..8) {
            val tag = FakeNtag21x(NtagModel.NTAG213)
            seed(tag)
            tag.loseTagAfter(tag.writeCount + writesBeforeLoss)

            val result = erase(FakeTagSession.ntag(tag))

            val area = parsed(tag)   // throws if the tag cannot be parsed any more
            assertEquals(5, area.leadingBytes, "the Lock Control TLV survives (loss after $writesBeforeLoss)")
            assertTrue(area.message == oldBytes || area.message.isEmpty(), "old or empty, never half and half (loss after $writesBeforeLoss)")
            when (result) {
                is Outcome.Ok -> {
                    sawErase = true
                    assertTrue(area.message.isEmpty())
                }
                is Outcome.Err -> assertEquals(TagError.TagLost, result.error, "loss after $writesBeforeLoss writes")
            }
        }
        assertTrue(sawErase, "with enough writes the erase completes")
    }

    @Test
    fun aTagLostAfterTheFirstPageStillReportsFailureNotSuccess() {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        seed(tag)
        tag.loseTagAfter(tag.writeCount + 1)

        val result = erase(FakeTagSession.ntag(tag))

        assertEquals(Outcome.Err(TagError.TagLost), result)
        assertTrue(parsed(tag).message.isEmpty(), "the length page went first, so the tag already reads empty")
    }

    // ---- the verify step -------------------------------------------------------------------------------------------

    @Test
    fun aTagThatAcknowledgesButIgnoresWritesIsAVerifyMismatchNeverSuccess() {
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

        val result = erase(ignoring)

        assertIs<Outcome.Err<TagError>>(result)
        assertIs<TagError.VerifyMismatch>(result.error)
    }

    // ---- bookkeeping -----------------------------------------------------------------------------------------------

    @Test
    fun reportsConnectReadWriteVerifyPhasesAndTheEraseKind() {
        val phases = mutableListOf<OperationPhase>()
        val tag = FakeNtag21x(NtagModel.NTAG213)
        seed(tag)

        erase(FakeTagSession.ntag(tag), phases)

        assertEquals(
            listOf(OperationPhase.CONNECTING, OperationPhase.READING, OperationPhase.WRITING, OperationPhase.VERIFYING),
            phases,
        )
        assertEquals(OperationKind.ERASE, EraseTagUseCase().kind)
    }

    @Test
    fun aTagLostWhileConnectingIsATagLostFailure() {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        tag.failNext(TagError.TagLost)

        val result = erase(FakeTagSession.ntag(tag, identified = false))

        assertEquals(Outcome.Err(TagError.TagLost), result)
        assertEquals(0, tag.writeCount)
    }

    // ---- tags without raw NTAG access ------------------------------------------------------------------------------

    @Test
    fun aGenericNdefTagIsErasedThroughThePlatformWithTheBlankMessage() {
        val session = FakeGenericNdefSession(message = uriMessage)

        val result = erase(session)

        assertIs<Outcome.Ok<EraseReport>>(result)
        assertTrue(result.value.hadContent)
        assertEquals(listOf(NdefMessage.BLANK), session.writes)
        assertNull((runBlocking { session.readNdef() } as Outcome.Ok).value)
    }

    @Test
    fun aGenericTagThatIsAlreadyEmptyIsNotWrittenAtAll() {
        val session = FakeGenericNdefSession(message = null)

        val result = erase(session)

        assertIs<Outcome.Ok<EraseReport>>(result)
        assertFalse(result.value.hadContent)
        assertEquals(emptyList(), session.writes)
    }

    @Test
    fun aReadOnlyGenericTagIsRefusedWithZeroWrites() {
        val session = FakeGenericNdefSession(message = uriMessage, writable = false)

        assertEquals(Outcome.Err(TagError.ReadOnly), erase(session))
        assertEquals(emptyList(), session.writes)
    }

    @Test
    fun mifareClassicWithoutNdefIsExplainedNotAttempted() {
        val result = erase(FakeTagSession.withoutNtag(TagFamily.MIFARE_CLASSIC))

        assertEquals(Outcome.Err(TagError.Unsupported(TagError.Unsupported.MIFARE_CLASSIC)), result)
    }

    @Test
    fun aTagWithNoNdefSupportAtAllIsNotNdef() {
        val result = erase(FakeTagSession.withoutNtag(TagFamily.UNKNOWN, setOf(TagTech.NFC_A)))

        assertEquals(Outcome.Err(TagError.NotNdef), result)
    }
}
