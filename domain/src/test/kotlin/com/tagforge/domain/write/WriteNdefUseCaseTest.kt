package com.tagforge.domain.write

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
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
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.tag.NdefInfo
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** TW-03 write + verify, TW-04 read-only/capacity, TW-05 overwrite confirmation, G4 TagLost, G5 destructive protocol. */
class WriteNdefUseCaseTest {

    private val uriMessage = NdefMessage(listOf(NdefRecord.uri("https://example.com")))   // 16 bytes
    private val oldMessage = NdefMessage(listOf(NdefRecord.text("old", "en")))

    private fun ntag(model: NtagModel = NtagModel.NTAG213) = FakeNtag21x(model)

    private fun write(
        message: NdefMessage,
        session: TagSession,
        confirm: suspend (NdefMessage?) -> Boolean = { true },
        phases: MutableList<OperationPhase> = mutableListOf(),
    ): TagResult<WriteReport> = kotlinx.coroutines.runBlocking {
        WriteNdefUseCase(message, confirmOverwrite = confirm).execute(session) { phases += it }
    }

    /** The NDEF content of a tag as a reader would see it, straight from the raw pages (works on a "lost" tag too). */
    private fun contentOf(tag: FakeNtag21x): Bytes? {
        val area = Bytes.concat((tag.model.userStart..tag.model.userEnd).map { tag.page(it) })
        return (Type2Tlv.unwrap(area) as? Outcome.Ok)?.value?.message
    }

    private fun seedWith(tag: FakeNtag21x, message: NdefMessage) {
        kotlinx.coroutines.runBlocking { FakeTagSession.ntag(tag).writeNdef(message) }
    }

    // ---- verified write ---------------------------------------------------------------------------------

    @Test
    fun verifiedWriteOnBlankNtag213Succeeds() = runTest {
        val tag = ntag()
        val session = FakeTagSession.ntag(tag)
        var asked = false

        val result = WriteNdefUseCase(uriMessage) { asked = true; true }.execute(session) {}

        assertIs<Outcome.Ok<WriteReport>>(result)
        assertEquals(16, DefaultNdefCodec.encode(uriMessage).size)
        assertEquals(uriMessage, (session.readNdef() as Outcome.Ok).value)
        assertEquals(16, result.value.messageSize)
        assertEquals(uriMessage, result.value.written)
        assertFalse(asked, "a blank tag needs no overwrite confirmation")
    }

    @Test
    fun theKindIsWrite() {
        assertEquals(OperationKind.WRITE, WriteNdefUseCase(uriMessage) { true }.kind)
    }

    @Test
    fun reportsConnectReadWriteVerifyPhasesInOrder() {
        val phases = mutableListOf<OperationPhase>()
        write(uriMessage, FakeTagSession.ntag(ntag()), phases = phases)
        assertEquals(
            listOf(OperationPhase.CONNECTING, OperationPhase.READING, OperationPhase.WRITING, OperationPhase.VERIFYING),
            phases,
        )
    }

    @Test
    fun writesTheSameMessageToNtag215AndNtag216Too() {
        for (model in listOf(NtagModel.NTAG215, NtagModel.NTAG216)) {
            val tag = ntag(model)
            val result = write(uriMessage, FakeTagSession.ntag(tag))
            assertIs<Outcome.Ok<WriteReport>>(result, "$model")
            assertEquals(DefaultNdefCodec.encode(uriMessage), DefaultNdefCodec.encode(readBack(tag)), "$model")
        }
    }

    private fun readBack(tag: FakeNtag21x): NdefMessage =
        (DefaultNdefCodec.decode(contentOf(tag)!!) as Outcome.Ok).value

    // ---- length written last ------------------------------------------------------------------------------

    @Test
    fun theLengthPageIsZeroedFirstAndWrittenLastOnNtag213() {
        val tag = ntag()
        write(uriMessage, FakeTagSession.ntag(tag))

        // NTAG213 user memory starts with the 5-byte Lock Control TLV, so `03 <len>` sits in page 5 (04 + 1)
        val headerPage = 5
        assertEquals(headerPage, tag.writtenPages.first(), "length is cleared before any body page changes")
        assertEquals(headerPage, tag.writtenPages.last(), "length is committed after the body")
        assertTrue(tag.writtenPages.size >= 3)
        assertEquals("34 03 10".hex(), tag.page(headerPage).slice(0, 3))   // last Lock Control byte, NDEF TLV, 0x10 = the 16 byte message
    }

    @Test
    fun theLengthPageIsFirstAndLastOnNtag215Too() {
        val tag = ntag(NtagModel.NTAG215)
        write(uriMessage, FakeTagSession.ntag(tag))
        assertEquals(4, tag.writtenPages.first())
        assertEquals(4, tag.writtenPages.last())
    }

    @Test
    fun aTagLostAtAnyPointLeavesOldEmptyOrCompleteContentNeverATruncatedMessage() {
        val newBytes = DefaultNdefCodec.encode(uriMessage)
        val oldBytes = DefaultNdefCodec.encode(oldMessage)
        for (writesBeforeLoss in 0..9) {
            val tag = ntag()
            seedWith(tag, oldMessage)
            tag.loseTagAfter(tag.writeCount + writesBeforeLoss)

            val result = write(uriMessage, FakeTagSession.ntag(tag))
            val content = contentOf(tag)

            if (result is Outcome.Ok) {
                assertEquals(newBytes, content, "completed write after $writesBeforeLoss writes")
            } else {
                assertEquals(TagError.TagLost, (result as Outcome.Err).error, "loss after $writesBeforeLoss writes")
                assertTrue(
                    content == oldBytes || content == Bytes.EMPTY || content == newBytes,
                    "after losing the tag following $writesBeforeLoss writes the content was $content",
                )
            }
        }
    }

    @Test
    fun tagLostAfterTheThirdPageWriteIsAFailureNeverASuccess() {
        val tag = ntag()
        tag.loseTagAfter(3)

        val result = write(uriMessage, FakeTagSession.ntag(tag))

        assertEquals(Outcome.Err(TagError.TagLost), result)
        assertEquals(Bytes.EMPTY, contentOf(tag), "length was cleared first, so the tag reads as an empty NDEF")
    }

    // ---- verification -------------------------------------------------------------------------------------

    /**
     * Behaves like [inner] until the first page write, then answers every raw read from [afterWrite] (an NTAG21x is
     * verified over raw NfcA, so this is where a corrupted or vanished read-back has to be injected).
     */
    private class ReadBackOverride(
        private val inner: TagSession,
        private val tag: FakeNtag21x,
        private val afterWrite: suspend (Bytes) -> TagResult<Bytes>,
    ) : TagSession by inner {
        override fun transceiver(tech: TagTech): TagTransceiver? = inner.transceiver(tech)?.let { real ->
            object : TagTransceiver by real {
                override suspend fun transceive(command: Bytes): TagResult<Bytes> =
                    if (tag.writeCount > 0 && command[0] == NtagCommands.FAST_READ) afterWrite(command) else real.transceive(command)
            }
        }
    }

    private fun shadowHolding(message: NdefMessage?): FakeNtag21x = FakeNtag21x(NtagModel.NTAG213).also { shadow ->
        if (message != null) seedWith(shadow, message)
    }

    @Test
    fun aMismatchOnReadBackIsReportedAsVerifyMismatch() {
        val tag = ntag()
        val corrupted = NdefMessage(listOf(NdefRecord.text("corrupted")))
        val session = ReadBackOverride(FakeTagSession.ntag(tag), tag, shadowHolding(corrupted)::transceive)

        val result = write(uriMessage, session)

        assertIs<Outcome.Err<TagError>>(result)
        val error = result.error
        assertIs<TagError.VerifyMismatch>(error)
        assertEquals(DefaultNdefCodec.encode(uriMessage), error.expected)
        assertEquals(DefaultNdefCodec.encode(corrupted), error.actual)
    }

    @Test
    fun anEmptyReadBackAfterWritingIsAMismatchWithNoActualBytes() {
        val tag = ntag()
        val session = ReadBackOverride(FakeTagSession.ntag(tag), tag, shadowHolding(null)::transceive)

        val error = (write(uriMessage, session) as Outcome.Err).error

        assertEquals(TagError.VerifyMismatch(DefaultNdefCodec.encode(uriMessage), null), error)
    }

    @Test
    fun aTagLostDuringTheReadBackIsATagLostFailureNotAMismatch() {
        val tag = ntag()
        val session = ReadBackOverride(FakeTagSession.ntag(tag), tag) { Outcome.Err(TagError.TagLost) }
        assertEquals(Outcome.Err(TagError.TagLost), write(uriMessage, session))
    }

    // ---- read-only and capacity ----------------------------------------------------------------------------

    private fun lockedSession(tag: FakeNtag21x): FakeTagSession {
        val open = FakeTagSession.ntag(tag)
        return FakeTagSession(open.info.copy(ndef = open.info.ndef!!.copy(isWritable = false)), tag)
    }

    @Test
    fun aTagReportedNotWritableIsReadOnlyWithZeroWrites() {
        val tag = ntag()
        val result = write(uriMessage, lockedSession(tag))
        assertEquals(Outcome.Err(TagError.ReadOnly), result)
        assertEquals(0, tag.writeCount)
    }

    @Test
    fun aWriteNakOnTheFirstPageAlsoMeansReadOnly() {
        val tag = ntag()
        // AUTH0 = 4 without read protection: everything stays readable but every user page refuses writes
        tag.setPage(tag.model.cfg0, "00 00 00 04".hex())

        val result = write(uriMessage, FakeTagSession.ntag(tag))

        assertEquals(Outcome.Err(TagError.ReadOnly), result)
        assertEquals(0, tag.writeCount)
    }

    @Test
    fun aMessageLargerThanTheTagIsRejectedBeforeAnyWrite() {
        val tag = ntag()
        val tooBig = NdefMessage(listOf(NdefRecord.text("a".repeat(131), "en")))   // 138 bytes, one over 137
        assertEquals(138, DefaultNdefCodec.encode(tooBig).size)

        val result = write(tooBig, FakeTagSession.ntag(tag))

        assertEquals(Outcome.Err(TagError.CapacityExceeded(required = 138, available = 137)), result)
        assertEquals(0, tag.writeCount)
    }

    @Test
    fun aMessageOfExactlyTheCapacityIsWrittenWithoutATerminatorAndVerifies() {
        val tag = ntag()
        val exact = NdefMessage(listOf(NdefRecord.text("a".repeat(130), "en")))   // 137 bytes
        assertEquals(137, DefaultNdefCodec.encode(exact).size)

        val result = write(exact, FakeTagSession.ntag(tag))

        assertIs<Outcome.Ok<WriteReport>>(result)
        assertEquals(DefaultNdefCodec.encode(exact), contentOf(tag))
        assertEquals("00 00 00 BD".hex(), tag.page(NtagModel.NTAG213.dynLock), "never spills into the lock pages")
    }

    /** A session that knows the chip model but has no Android `Ndef` info (the OS did not expose the NDEF technology). */
    private class NoNdefInfoSession(private val inner: FakeTagSession) : TagSession by inner {
        override val info: TagInfo get() = inner.info.copy(ndef = null)
        override suspend fun identify(): TagResult<TagInfo> = when (val r = inner.identify()) {
            is Outcome.Ok -> Outcome.Ok(r.value.copy(ndef = null))
            is Outcome.Err -> r
        }
    }

    /** S3: with no Ndef info the use case must accept exactly what the wizard meter accepts (androidMaxSize). */
    @Test
    fun anExactFit137ByteMessageIsAcceptedEvenWithoutNdefInfo() {
        val tag = ntag()
        val exact = NdefMessage(listOf(NdefRecord.text("a".repeat(130), "en")))   // 137 bytes
        assertEquals(137, DefaultNdefCodec.encode(exact).size)

        val result = write(exact, NoNdefInfoSession(FakeTagSession.ntag(tag)))

        assertIs<Outcome.Ok<WriteReport>>(result)
        assertEquals(DefaultNdefCodec.encode(exact), contentOf(tag))
    }

    /** Header of a single `application/x-tf` MIME record: flags, type length, payload length (1 B short form, 4 B long), 16 B type. */
    private fun mimeHeaderSize(totalSize: Int): Int = if (totalSize - 19 <= 255) 19 else 22

    @Test
    fun withoutNdefInfoOneByteOverTheMeterCapacityIsStillRejectedBeforeAnyWrite() {
        for ((model, capacity) in listOf(NtagModel.NTAG213 to 137, NtagModel.NTAG215 to 492, NtagModel.NTAG216 to 868)) {
            val tag = ntag(model)
            val tooBig = NdefMessage(listOf(NdefRecord.mime("application/x-tf", Bytes.of(*IntArray(capacity + 1 - mimeHeaderSize(capacity + 1)) { 7 }))))
            val size = DefaultNdefCodec.encode(tooBig).size
            assertEquals(capacity + 1, size, "$model: fixture is one byte over")

            val result = write(tooBig, NoNdefInfoSession(FakeTagSession.ntag(tag)))

            assertEquals(Outcome.Err(TagError.CapacityExceeded(required = capacity + 1, available = capacity)), result, "$model")
            assertEquals(0, tag.writeCount, "$model")
        }
    }

    @Test
    fun theSameMessageFitsAnNtag215() {
        val tag = ntag(NtagModel.NTAG215)
        val tooBigFor213 = NdefMessage(listOf(NdefRecord.text("a".repeat(131), "en")))
        assertIs<Outcome.Ok<WriteReport>>(write(tooBigFor213, FakeTagSession.ntag(tag)))
    }

    // ---- overwrite confirmation ---------------------------------------------------------------------------

    @Test
    fun existingContentTriggersAConfirmationThatReceivesIt() {
        val tag = ntag()
        seedWith(tag, oldMessage)
        var shown: NdefMessage? = null
        val before = tag.writeCount

        write(uriMessage, FakeTagSession.ntag(tag), confirm = { existing -> shown = existing; true })

        assertEquals(oldMessage, shown)
        assertEquals(uriMessage, readBack(tag))
        assertTrue(tag.writeCount > before)
    }

    @Test
    fun decliningTheConfirmationMeansCancelledAndZeroWrites() {
        val tag = ntag()
        seedWith(tag, oldMessage)
        val before = tag.writeCount

        val result = write(uriMessage, FakeTagSession.ntag(tag), confirm = { false })

        assertEquals(Outcome.Err(TagError.Cancelled), result)
        assertEquals(before, tag.writeCount, "dismissing the confirmation must not touch the tag")
        assertEquals(oldMessage, readBack(tag))
    }

    // ---- tags without raw access -----------------------------------------------------------------------------

    @Test
    fun tagsWithoutARawChannelAreWrittenThroughTheSessionAndStillVerified() {
        val session = RecordingSession()

        val result = write(uriMessage, session)

        assertIs<Outcome.Ok<WriteReport>>(result)
        assertEquals(listOf(uriMessage), session.writes)
    }

    @Test
    fun aRecordingSessionThatCorruptsTheWriteFailsVerification() {
        val session = RecordingSession(corrupt = true)
        val error = (write(uriMessage, session) as Outcome.Err).error
        assertIs<TagError.VerifyMismatch>(error)
    }

    @Test
    fun anIoErrorInTheMiddleOfTheOperationIsPropagatedAsIs() {
        val tag = ntag()
        tag.failNextAfter(transceives = 8, error = TagError.Io("rf noise"))
        assertEquals(Outcome.Err(TagError.Io("rf noise")), write(uriMessage, FakeTagSession.ntag(tag)))
    }

    /** Some NFC controllers swallow the 4-bit ACK of a WRITE and hand back an empty response. */
    private class AckSwallowingSession(private val inner: FakeTagSession) : TagSession by inner {
        override fun transceiver(tech: TagTech): TagTransceiver? = inner.transceiver(tech)?.let { real ->
            object : TagTransceiver by real {
                override suspend fun transceive(command: Bytes): TagResult<Bytes> {
                    val response = real.transceive(command)
                    return if (command[0] == 0xA2 && response is Outcome.Ok) Outcome.Ok(Bytes.EMPTY) else response
                }
            }
        }
    }

    @Test
    fun anEmptyWriteResponseIsNotAFailureBecauseTheReadBackProvesTheWrite() {
        val tag = ntag()
        val result = write(uriMessage, AckSwallowingSession(FakeTagSession.ntag(tag)))
        assertIs<Outcome.Ok<WriteReport>>(result)
        assertEquals(DefaultNdefCodec.encode(uriMessage), contentOf(tag))
    }

    @Test
    fun anEmptyWriteResponseCannotHideAWriteThatDidNotLand() {
        val tag = ntag()
        // reads after the writes keep seeing the old (empty) tag, as if the writes had been dropped
        val session = ReadBackOverride(AckSwallowingSession(FakeTagSession.ntag(tag)), tag, shadowHolding(null)::transceive)
        assertIs<TagError.VerifyMismatch>((write(uriMessage, session) as Outcome.Err).error)
    }

    /** A tag that only supports the generic NDEF path (no NfcA transceiver), like an Android `Ndef`-only tag. */
    private class RecordingSession(private val corrupt: Boolean = false) : TagSession {
        val writes = mutableListOf<NdefMessage>()
        private var stored: NdefMessage? = null

        override val info = TagInfo(
            uid = "04 11 22 33".hex(),
            techs = setOf(TagTech.NDEF),
            family = com.tagforge.domain.tag.TagFamily.UNKNOWN,
            ndef = NdefInfo("org.nfcforum.ndef.type4", maxSize = 2048, isWritable = true, canMakeReadOnly = false),
        )

        override suspend fun identify(): TagResult<TagInfo> = Outcome.Ok(info)
        override fun transceiver(tech: TagTech): TagTransceiver? = null
        override suspend fun readNdef(): TagResult<NdefMessage?> = Outcome.Ok(stored)
        override suspend fun writeNdef(message: NdefMessage): TagResult<Unit> {
            writes += message
            stored = if (corrupt) NdefMessage(listOf(NdefRecord.text("garbled"))) else message
            return Outcome.Ok(Unit)
        }
        override suspend fun formatNdef(initial: NdefMessage?): TagResult<Unit> = Outcome.Ok(Unit)
        override suspend fun makeReadOnly(): TagResult<Unit> = Outcome.Ok(Unit)
        override suspend fun close() = Unit
    }
}
