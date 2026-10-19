package com.tagforge.domain.tools

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
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

/** What a verified format did. [changed] is false when the tag already was clean and empty (nothing was written). */
data class FormatReport(val info: TagInfo, val changed: Boolean)

/**
 * Brings the next tag to a clean, empty NDEF state (TT-02) and proves it by reading the tag back. As with erase, the
 * caller has already obtained the user's confirmation (G5).
 *
 * NTAG21x with a capability container: rewrites the empty NDEF TLV behind the Lock Control TLV (kept if present,
 * restored from the factory layout when the area is zeroed or corrupt, e.g. NTAG213's `01 03 A0 0C 34`) and zeroes the
 * rest of the user memory. It never writes pages 0-3, the capability container, lock bytes or config pages. Tags that
 * are not formatted at all are formatted by the platform (`NdefFormatable`). A locked tag is refused before any write.
 */
class FormatTagUseCase : TagOperation<FormatReport> {

    override val kind: OperationKind = OperationKind.FORMAT

    /** Wiping a whole NTAG216 is up to 222 page writes: more than the 10 s default is not needed but is kind. */
    override val timeoutMs: Long = FORMAT_TIMEOUT_MS

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<FormatReport> {
        phase(OperationPhase.CONNECTING)
        val info = when (val identified = session.identify()) {
            is Outcome.Err -> return identified
            is Outcome.Ok -> identified.value
        }
        if (info.family == TagFamily.MIFARE_CLASSIC && TagTech.NDEF !in info.techs && TagTech.NDEF_FORMATABLE !in info.techs) {
            return Outcome.Err(TagError.Unsupported(TagError.Unsupported.MIFARE_CLASSIC))
        }
        if (info.ndef?.isWritable == false) return Outcome.Err(TagError.ReadOnly)

        val model = info.layout
        val transceiver = session.transceiver(TagTech.NFC_A)
        return if (model != null && transceiver != null) {
            formatType2(session, info, transceiver, model, phase)
        } else {
            formatThroughPlatform(session, info, phase)
        }
    }

    // ---- NTAG21x: raw pages -------------------------------------------------------------------------------------------

    private suspend fun formatType2(
        session: TagSession,
        info: TagInfo,
        transceiver: TagTransceiver,
        model: Type2Layout,
        phase: (OperationPhase) -> Unit,
    ): TagResult<FormatReport> {
        phase(OperationPhase.READING)
        var area = when (val read = Type2Io.readCcAndUserArea(transceiver, model)) {
            is Outcome.Err -> return read
            is Outcome.Ok -> read.value
        }
        if (!area.hasCapabilityContainer) {
            // Blank Ultralight-family page 3: initialise it over the same channel instead of hopping technologies.
            area = when (val init = Type2Io.initCapabilityContainer(transceiver, model, area)) {
                is Outcome.Err -> return init
                is Outcome.Ok -> init.value ?: return formatThroughPlatform(session, info, phase)
            }
        }
        if (area.isReadOnlyPerCc) return Outcome.Err(TagError.ReadOnly)

        val current = area.user
        val leading = (Type2Tlv.unwrap(current) as? Outcome.Ok)?.value?.leadingBytes?.let { current.slice(0, it) }
            ?: model.factoryLockControlTlv
            ?: Bytes.EMPTY

        val steps = Type2ErasePlanner.plan(model, current, leading, wipeEnd = current.size)
        if (steps.isEmpty()) return Outcome.Ok(FormatReport(session.info, changed = false))

        phase(OperationPhase.WRITING)
        val written = Type2Io.execute(transceiver, steps)
        if (written is Outcome.Err) return written

        phase(OperationPhase.VERIFYING)
        val after = when (val read = Type2Io.readCcAndUserArea(transceiver, model)) {
            is Outcome.Err -> return read
            is Outcome.Ok -> read.value.user
        }
        val expected = Type2Tlv.wrap(Bytes.EMPTY, leading)
        val head = after.slice(0, minOf(expected.size, after.size))
        val clean = (expected.size until after.size).all { after[it] == 0 }
        if (head != expected || !clean) return Outcome.Err(TagError.VerifyMismatch(expected, head))
        return Outcome.Ok(FormatReport(session.info, changed = true))
    }

    // ---- NdefFormatable --------------------------------------------------------------------------------------------------

    private suspend fun formatThroughPlatform(session: TagSession, info: TagInfo, phase: (OperationPhase) -> Unit): TagResult<FormatReport> {
        if (TagTech.NDEF_FORMATABLE !in info.techs) return Outcome.Err(TagError.Unsupported(FORMAT))

        phase(OperationPhase.WRITING)
        val formatted = session.formatNdef(null)
        if (formatted is Outcome.Err) return formatted

        phase(OperationPhase.VERIFYING)
        return when (val after = session.readNdef()) {
            is Outcome.Ok -> if (after.value == null) {
                Outcome.Ok(FormatReport(session.info, changed = true))
            } else {
                Outcome.Err(TagError.VerifyMismatch(Bytes.EMPTY, null))
            }
            is Outcome.Err -> when (after.error) {
                // Android cannot re-read a Tag handle that was formatted through it (its tech list predates the format);
                // `NdefFormatable.format` itself throws on failure, so "no NDEF visible on this handle" is not a failure.
                TagError.NotNdef -> Outcome.Ok(FormatReport(session.info, changed = true))
                is TagError.Malformed -> Outcome.Err(TagError.VerifyMismatch(Bytes.EMPTY, null))
                else -> after
            }
        }
    }

    private companion object {
        const val FORMAT_TIMEOUT_MS = 30_000L
        const val FORMAT = "format"
    }
}
