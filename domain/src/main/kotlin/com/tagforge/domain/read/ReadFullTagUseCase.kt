package com.tagforge.domain.read

import com.tagforge.domain.capability.TagCapabilities
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.MemoryDump
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError

/**
 * What one tap gives the Read screen: the NDEF readout plus, when the chip allows it, the raw pages.
 * A missing dump never hides the readout; [dumpError] says why it is missing. A null dump with a null error means
 * the dump was not attempted because it would need another technology: it lives in Tools.
 */
data class TagScan(
    val readout: TagReadout,
    val dump: MemoryDump?,
    val dumpError: TagError?,
)

class ReadFullTagUseCase(
    private val readTag: ReadTagUseCase = ReadTagUseCase(),
    private val readMemory: ReadMemoryUseCase = ReadMemoryUseCase(),
) : TagOperation<TagScan> {

    override val kind: OperationKind = OperationKind.READ

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<TagScan> {
        val readout = when (val result = readTag.execute(session, phase)) {
            is Outcome.Err -> return result
            is Outcome.Ok -> result.value
        }
        // The raw dump only joins the Read screen when it stays on the channel that read the NDEF (one technology).
        if (!TagCapabilities.of(readout.info).readIncludesDump) return Outcome.Ok(TagScan(readout, dump = null, dumpError = null))
        // The connection phase was already reported by the readout; do not flash it again.
        val dumpResult = readMemory.execute(session) { reported -> if (reported != OperationPhase.CONNECTING) phase(reported) }
        return when (dumpResult) {
            is Outcome.Ok -> Outcome.Ok(TagScan(readout.copy(info = session.info), dumpResult.value, null))
            is Outcome.Err -> Outcome.Ok(TagScan(readout.copy(info = session.info), null, dumpResult.error))
        }
    }
}
