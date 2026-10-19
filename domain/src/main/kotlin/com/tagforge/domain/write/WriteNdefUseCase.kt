package com.tagforge.domain.write

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefBudget
import com.tagforge.domain.ndef.NdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.Type2Tlv
import com.tagforge.domain.type2.Type2Layout
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
        when (val declined = OverwriteGuard.check(session, confirmOverwrite)) {
            null -> Unit
            else -> return declined
        }

        phase(OperationPhase.WRITING)
        val written = writeMessage(session, info, expected)
        if (written is Outcome.Err) return written

        phase(OperationPhase.VERIFYING)
        return verify(session, expected)
    }

    /** Same number the wizard meter shows: what Android's `Ndef.maxSize` reports, or its offline equivalent per chip. */
    private fun capacityOf(info: TagInfo): Int? = info.ndef?.maxSize ?: info.layout?.let(NdefBudget::androidMaxSize)

    private suspend fun writeMessage(session: TagSession, info: TagInfo, encoded: Bytes): TagResult<Unit> {
        val model = info.layout
        val transceiver = session.transceiver(TagTech.NFC_A)
        if (model != null && transceiver != null) {
            writeType2(transceiver, model, encoded)?.let { return it }
        }
        return session.writeNdef(message)
    }

    /**
     * Null means "this chip is not set up for raw NDEF writing, use the generic path". Otherwise the final result.
     */
    private suspend fun writeType2(transceiver: TagTransceiver, model: Type2Layout, encoded: Bytes): TagResult<Unit>? {
        var area = when (val read = Type2Io.readCcAndUserArea(transceiver, model)) {
            is Outcome.Err -> return read
            is Outcome.Ok -> read.value
        }
        if (!area.hasCapabilityContainer) {
            // Blank Ultralight-family page 3: write the capability container over the SAME channel; anything else
            // without a CC is left to the platform formatter.
            area = when (val init = Type2Io.initCapabilityContainer(transceiver, model, area)) {
                is Outcome.Err -> return init
                is Outcome.Ok -> init.value ?: return null
            }
        }
        val current = area.user

        val leading = (Type2Tlv.unwrap(current) as? Outcome.Ok)?.value?.leadingBytes?.let { current.slice(0, it) }
            ?: model.factoryLockControlTlv ?: Bytes.EMPTY
        val steps = Type2WritePlanner.plan(model, current, encoded, leading, area.ndefAreaFor(model))
            ?: return Outcome.Err(TagError.CapacityExceeded(encoded.size, NdefBudget.androidMaxSize(model)))

        return Type2Io.execute(transceiver, steps)
    }

    private suspend fun verify(session: TagSession, expected: Bytes): TagResult<WriteReport> {
        val actual = when (val read = Type2Ndef.read(session, codec)) {
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
    }
}
