package com.tagforge.domain.write

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefBudget
import com.tagforge.domain.ndef.NdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.Type2Tlv
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ntag.NtagResponses
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech

/** What a successful, VERIFIED write produced. [messageSize] is the encoded NDEF size without TLV framing. */
data class WriteReport(val info: TagInfo, val written: NdefMessage, val messageSize: Int)

/**
 * Writes [message] to the next tag and proves it by reading it back (TW-03).
 *
 * Order of business: identify -> refuse locked/too-small tags (zero writes) -> if the tag already holds data, ask
 * [confirmOverwrite] (declining = `Cancelled`, zero writes, G5) -> write -> read back and compare.
 *
 * NTAG21x chips are written page by page through [Type2WritePlanner] (length cleared first, written last) so a tag
 * pulled away mid-write reads as empty or complete, never as a truncated message. Other tags go through
 * [TagSession.writeNdef].
 *
 * @param confirmOverwrite called with the existing message, or null when the tag holds data that cannot be parsed
 */
class WriteNdefUseCase(
    private val message: NdefMessage,
    private val codec: NdefCodec = DefaultNdefCodec,
    private val confirmOverwrite: suspend (existing: NdefMessage?) -> Boolean,
) : TagOperation<WriteReport> {

    override val kind: OperationKind = OperationKind.WRITE

    /** The user may take their time reading the overwrite warning, so a write gets longer than a plain read. */
    override val timeoutMs: Long = CONFIRMATION_TIMEOUT_MS

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<WriteReport> {
        phase(OperationPhase.CONNECTING)
        val info = when (val identified = session.identify()) {
            is Outcome.Err -> return identified
            is Outcome.Ok -> identified.value
        }
        if (info.ndef?.isWritable == false) return Outcome.Err(TagError.ReadOnly)

        val expected = codec.encode(message)
        capacityOf(info)?.let { capacity ->
            if (expected.size > capacity) return Outcome.Err(TagError.CapacityExceeded(expected.size, capacity))
        }

        phase(OperationPhase.READING)
        when (val declined = confirmAgainstExistingContent(session)) {
            null -> Unit
            else -> return declined
        }

        phase(OperationPhase.WRITING)
        val written = writeMessage(session, info, expected)
        if (written is Outcome.Err) return written

        phase(OperationPhase.VERIFYING)
        return verify(session, expected)
    }

    /** Null = go ahead. Otherwise the error to return (cancelled by the user, or the tag could not be inspected). */
    private suspend fun confirmAgainstExistingContent(session: TagSession): Outcome.Err<TagError>? =
        when (val read = session.readNdef()) {
            is Outcome.Ok -> {
                val existing = read.value
                if (existing == null || confirmOverwrite(existing)) null else Outcome.Err(TagError.Cancelled)
            }
            is Outcome.Err -> when (read.error) {
                TagError.NotNdef -> null   // blank tag: nothing to lose
                is TagError.Malformed -> if (confirmOverwrite(null)) null else Outcome.Err(TagError.Cancelled)
                is TagError.Nak, TagError.AuthRequired -> Outcome.Err(TagError.AuthRequired)
                else -> read
            }
        }

    private fun capacityOf(info: TagInfo): Int? = info.ndef?.maxSize ?: info.ntag?.let(NdefBudget::presetCapacity)

    private suspend fun writeMessage(session: TagSession, info: TagInfo, encoded: Bytes): TagResult<Unit> {
        val model = info.ntag
        val transceiver = session.transceiver(TagTech.NFC_A)
        if (model != null && transceiver != null) {
            writeType2(transceiver, model, encoded)?.let { return it }
        }
        return session.writeNdef(message)
    }

    /**
     * Null means "this chip is not set up for raw NDEF writing, use the generic path". Otherwise the final result.
     */
    private suspend fun writeType2(transceiver: TagTransceiver, model: NtagModel, encoded: Bytes): TagResult<Unit>? {
        val area = when (val read = readCcAndUserArea(transceiver, model)) {
            is Outcome.Err -> return read
            is Outcome.Ok -> read.value
        }
        if (area.slice(0, 1)[0] != CC_MAGIC) return null   // capability container missing: let the platform format it
        val current = area.slice(NtagCommands.PAGE_SIZE)

        val leading = (Type2Tlv.unwrap(current) as? Outcome.Ok)?.value?.leadingBytes?.let { current.slice(0, it) }
            ?: model.factoryLockControlTlv ?: Bytes.EMPTY
        val steps = Type2WritePlanner.plan(model, current, encoded, leading)
            ?: return Outcome.Err(TagError.CapacityExceeded(encoded.size, NdefBudget.presetCapacity(model)))

        for ((index, step) in steps.withIndex()) {
            val response = when (val sent = transceiver.transceive(NtagCommands.write(step.page, step.data))) {
                is Outcome.Err -> return sent
                is Outcome.Ok -> sent.value
            }
            when (val ack = NtagResponses.checkAck(response)) {
                is Outcome.Ok -> Unit
                is Outcome.Err ->
                    // a refusal before anything changed means the tag is write-protected
                    return if (index == 0 && ack.error is TagError.Nak) Outcome.Err(TagError.ReadOnly) else ack
            }
        }
        return Outcome.Ok(Unit)
    }

    /** Page 3 (capability container) followed by the user pages, read in small FAST_READ chunks. */
    private suspend fun readCcAndUserArea(transceiver: TagTransceiver, model: NtagModel): TagResult<Bytes> {
        val chunks = ArrayList<Bytes>()
        var page = CC_PAGE
        while (page <= model.userEnd) {
            val end = minOf(page + READ_CHUNK_PAGES - 1, model.userEnd)
            val response = when (val sent = transceiver.transceive(NtagCommands.fastRead(page, end))) {
                is Outcome.Err -> return sent
                is Outcome.Ok -> sent.value
            }
            when (val checked = NtagResponses.checkData(response, (end - page + 1) * NtagCommands.PAGE_SIZE)) {
                is Outcome.Err -> return Outcome.Err(if (checked.error is TagError.Nak) TagError.AuthRequired else checked.error)
                is Outcome.Ok -> chunks += checked.value
            }
            page = end + 1
        }
        return Outcome.Ok(Bytes.concat(chunks))
    }

    private suspend fun verify(session: TagSession, expected: Bytes): TagResult<WriteReport> {
        val actual = when (val read = session.readNdef()) {
            is Outcome.Ok -> read.value
            is Outcome.Err -> return when (read.error) {
                is TagError.Malformed, TagError.NotNdef -> Outcome.Err(TagError.VerifyMismatch(expected, null))
                else -> read
            }
        }
        val actualBytes = actual?.let(codec::encode)
        if (actualBytes != expected) return Outcome.Err(TagError.VerifyMismatch(expected, actualBytes))
        return Outcome.Ok(WriteReport(session.info, message, expected.size))
    }

    private companion object {
        const val CONFIRMATION_TIMEOUT_MS = 60_000L
        const val CC_PAGE = 3
        const val CC_MAGIC = 0xE1
        const val READ_CHUNK_PAGES = 4
    }
}
