package com.tagforge.domain.tools

import com.tagforge.domain.capability.TagCapabilities
import com.tagforge.domain.capability.ToolOperation
import com.tagforge.domain.capability.refusal
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.MemoryDump
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.read.ReadMemoryUseCase
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo

/** The pages of one tag plus who it was, ready to show (annotated) and to export. */
data class DumpReport(val info: TagInfo, val dump: MemoryDump)

/**
 * The Tools "Memory dump": reads every page the tag allows (TT-03). Pure read, no writes. MIFARE Classic is
 * explained instead of probed: a phone without the NXP controller cannot dump it and raw NfcA READs would only fail.
 */
class DumpMemoryUseCase(private val readMemory: ReadMemoryUseCase = ReadMemoryUseCase()) : TagOperation<DumpReport> {

    override val kind: OperationKind = OperationKind.MEMORY_DUMP

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<DumpReport> {
        phase(OperationPhase.CONNECTING)
        val info = when (val identified = session.identify()) {
            is Outcome.Err -> return identified
            is Outcome.Ok -> identified.value
        }
        TagCapabilities.of(info).refusal(ToolOperation.DUMP)?.let { return Outcome.Err(it) }
        return when (val read = readMemory.execute(session) { reported -> if (reported != OperationPhase.CONNECTING) phase(reported) }) {
            is Outcome.Err -> read
            is Outcome.Ok -> Outcome.Ok(DumpReport(session.info, read.value))
        }
    }
}
