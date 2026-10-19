package com.tagforge.domain.tools

import com.tagforge.domain.capability.TagCapabilities
import com.tagforge.domain.capability.ToolOperation
import com.tagforge.domain.capability.refusal
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefParseError
import com.tagforge.domain.ndef.Type2Tlv
import com.tagforge.domain.type2.Type2Layout
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech
import com.tagforge.domain.write.Type2Io

/** What a verified erase did. [hadContent] is false when the tag was already empty (nothing was written). */
data class EraseReport(val info: TagInfo, val hadContent: Boolean)

/**
 * Erases the NDEF message of the next tag (TT-01) and proves it by reading the tag back.
 *
 * The caller has ALREADY obtained the user's confirmation (G5): arming this operation is the point of no return, and
 * a cancelled confirmation never reaches it. NTAG21x: writes the empty NDEF TLV `03 00 FE` behind the existing
 * Lock/Memory Control TLVs (NTAG213's `01 03 A0 0C 34` stays), zeroes the old message, and touches nothing outside
 * the NDEF area: not pages 0-3, not the lock bytes, not the capability container, not the config pages. Every other
 * NDEF tag goes through the platform with a single blank record.
 */
class EraseTagUseCase : TagOperation<EraseReport> {

    override val kind: OperationKind = OperationKind.ERASE

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<EraseReport> {
        phase(OperationPhase.CONNECTING)
        val info = when (val identified = session.identify()) {
            is Outcome.Err -> return identified
            is Outcome.Ok -> identified.value
        }
        TagCapabilities.of(info).refusal(ToolOperation.ERASE)?.let { return Outcome.Err(it) }
        if (info.ndef?.isWritable == false) return Outcome.Err(TagError.ReadOnly)

        val model = info.layout
        val transceiver = session.transceiver(TagTech.NFC_A)
        return if (model != null && transceiver != null) {
            eraseType2(session, transceiver, model, phase)
        } else {
            eraseGeneric(session, phase)
        }
    }

    // ---- NTAG21x: raw pages -------------------------------------------------------------------------------------------

    private suspend fun eraseType2(
        session: TagSession,
        transceiver: TagTransceiver,
        model: Type2Layout,
        phase: (OperationPhase) -> Unit,
    ): TagResult<EraseReport> {
        phase(OperationPhase.READING)
        val area = when (val read = Type2Io.readCcAndUserArea(transceiver, model)) {
            is Outcome.Err -> return read
            is Outcome.Ok -> read.value
        }
        if (!area.hasCapabilityContainer) return Outcome.Err(TagError.NotNdef)
        if (area.isReadOnlyPerCc) return Outcome.Err(TagError.ReadOnly)

        val current = area.user
        val tlv = when (val unwrapped = Type2Tlv.unwrap(current)) {
            is Outcome.Ok -> unwrapped.value
            is Outcome.Err -> return Outcome.Err(
                if (unwrapped.error == NdefParseError.NoNdefTlv) TagError.NotNdef else TagError.Malformed(unwrapped.error.toString()),
            )
        }
        val leading = current.slice(0, tlv.leadingBytes)
        val hadContent = !tlv.message.isEmpty()
        val oldExtent = tlv.leadingBytes + headerSize(tlv.message.size) + tlv.message.size + TERMINATOR_SIZE

        val steps = Type2ErasePlanner.plan(model, current, leading, wipeEnd = oldExtent)
        if (steps.isEmpty()) return Outcome.Ok(EraseReport(session.info, hadContent))

        phase(OperationPhase.WRITING)
        val written = Type2Io.execute(transceiver, steps)
        if (written is Outcome.Err) return written

        phase(OperationPhase.VERIFYING)
        return verifyType2(session, transceiver, model, leading, oldExtent, hadContent)
    }

    private suspend fun verifyType2(
        session: TagSession,
        transceiver: TagTransceiver,
        model: Type2Layout,
        leading: Bytes,
        oldExtent: Int,
        hadContent: Boolean,
    ): TagResult<EraseReport> {
        val after = when (val read = Type2Io.readCcAndUserArea(transceiver, model)) {
            is Outcome.Err -> return read
            is Outcome.Ok -> read.value.user
        }
        val expected = Type2Tlv.wrap(Bytes.EMPTY, leading)
        val head = after.slice(0, minOf(expected.size, after.size))
        val scrubbed = (expected.size until minOf(oldExtent, after.size)).all { after[it] == 0 }
        if (head != expected || !scrubbed) return Outcome.Err(TagError.VerifyMismatch(expected, head))
        return Outcome.Ok(EraseReport(session.info, hadContent))
    }

    private fun headerSize(messageSize: Int): Int = 1 + if (messageSize <= MAX_SHORT_LENGTH) 1 else LONG_LENGTH_FIELD

    // ---- everything else: through the platform -----------------------------------------------------------------------

    private suspend fun eraseGeneric(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<EraseReport> {
        phase(OperationPhase.READING)
        val hadContent = when (val read = session.readNdef()) {
            is Outcome.Ok -> read.value != null
            is Outcome.Err -> when (read.error) {
                is TagError.Malformed -> true   // unreadable content still counts as something to remove
                is TagError.Nak -> return Outcome.Err(TagError.AuthRequired)
                else -> return read
            }
        }
        if (!hadContent) return Outcome.Ok(EraseReport(session.info, hadContent = false))

        phase(OperationPhase.WRITING)
        val written = session.writeNdef(NdefMessage.BLANK)
        if (written is Outcome.Err) return written

        phase(OperationPhase.VERIFYING)
        return when (val after = session.readNdef()) {
            is Outcome.Ok -> if (after.value == null) {
                Outcome.Ok(EraseReport(session.info, hadContent = true))
            } else {
                Outcome.Err(TagError.VerifyMismatch(Bytes.EMPTY, null))
            }
            is Outcome.Err -> after
        }
    }

    private companion object {
        const val MAX_SHORT_LENGTH = 254
        const val LONG_LENGTH_FIELD = 3
        const val TERMINATOR_SIZE = 1
    }
}
