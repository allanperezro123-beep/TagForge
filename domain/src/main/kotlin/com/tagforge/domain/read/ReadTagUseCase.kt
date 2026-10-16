package com.tagforge.domain.read

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.ParsedRecord
import com.tagforge.domain.ndef.RecordInterpreter
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech

/** Why a readout has no message even though the read itself succeeded. Mapped to text by the UI. */
sealed interface ReadNotice {
    /** The NDEF area exists but cannot be parsed; the hex view still works. */
    data class NdefUnreadable(val detail: String) : ReadNotice

    /** The tag holds no NDEF structure (blank/zeroed memory or a non-NDEF tag). */
    data object NotNdef : ReadNotice

    /** The tag family cannot be read by this device or app (e.g. MIFARE Classic without NXP controller). */
    data class UnsupportedFamily(val family: TagFamily) : ReadNotice
}

/**
 * Everything the Read screen shows. [rawNdef] is the message re-encoded canonically (null when there is none).
 */
data class TagReadout(
    val info: TagInfo,
    val message: NdefMessage?,
    val parsed: List<ParsedRecord>,
    val rawNdef: Bytes?,
    val notice: ReadNotice? = null,
)

class ReadTagUseCase(private val codec: NdefCodec = DefaultNdefCodec) : TagOperation<TagReadout> {

    override val kind: OperationKind = OperationKind.READ

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<TagReadout> {
        phase(OperationPhase.CONNECTING)
        val info = when (val identified = session.identify()) {
            is Outcome.Err -> return identified
            is Outcome.Ok -> identified.value
        }

        phase(OperationPhase.READING)
        if (info.family == TagFamily.MIFARE_CLASSIC && TagTech.NDEF !in info.techs) {
            return Outcome.Ok(readout(info, null, ReadNotice.UnsupportedFamily(info.family)))
        }

        return when (val read = session.readNdef()) {
            is Outcome.Ok -> Outcome.Ok(readout(info, read.value, null))
            is Outcome.Err -> when (val error = read.error) {
                TagError.NotNdef -> Outcome.Ok(readout(info, null, ReadNotice.NotNdef))
                is TagError.Malformed -> Outcome.Ok(readout(info, null, ReadNotice.NdefUnreadable(error.detail)))
                is TagError.Nak, TagError.AuthRequired -> Outcome.Err(TagError.AuthRequired)
                else -> read
            }
        }
    }

    private fun readout(info: TagInfo, message: NdefMessage?, notice: ReadNotice?): TagReadout {
        val parsed = message?.let(RecordInterpreter::interpret).orEmpty()
        return TagReadout(info, message, parsed, message?.let(codec::encode), notice)
    }
}
