package com.tagforge.domain.tools

import com.tagforge.domain.capability.AccessChannel
import com.tagforge.domain.capability.Support
import com.tagforge.domain.capability.TagCapabilities
import com.tagforge.domain.capability.ToolOperation
import com.tagforge.domain.capability.refusal
import com.tagforge.domain.classic.ClassicReader
import com.tagforge.domain.classic.SafeClassic
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.MemoryDump
import com.tagforge.domain.iso15693.Iso15693
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.read.ReadIso15693MemoryUseCase
import com.tagforge.domain.read.ReadMemoryUseCase
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech

/** The pages of one tag plus who it was, ready to show (annotated) and to export. */
data class DumpReport(val info: TagInfo, val dump: MemoryDump)

/**
 * The Tools "Memory dump": reads every page the tag allows (TT-03). Pure read, no writes. MIFARE Classic is
 * explained instead of probed: a phone without the NXP controller cannot dump it and raw NfcA READs would only fail.
 */
class DumpMemoryUseCase(
    private val readMemory: ReadMemoryUseCase = ReadMemoryUseCase(),
    private val readIso15693: ReadIso15693MemoryUseCase = ReadIso15693MemoryUseCase(),
) : TagOperation<DumpReport> {

    override val kind: OperationKind = OperationKind.MEMORY_DUMP

    /** A MIFARE Classic card with private keys can cost one failed authentication per key and sector. */
    override val timeoutMs: Long = DUMP_TIMEOUT_MS

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<DumpReport> {
        phase(OperationPhase.CONNECTING)
        val info = when (val identified = session.identify()) {
            is Outcome.Err -> return identified
            is Outcome.Ok -> identified.value
        }
        val capabilities = TagCapabilities.of(info)
        capabilities.refusal(ToolOperation.DUMP)?.let { return Outcome.Err(it) }
        val channel = (capabilities[ToolOperation.DUMP] as Support.Available).channel
        if (channel == AccessChannel.RAW_NFC_V) return dumpIso15693(session, info, phase)
        if (channel == AccessChannel.CLASSIC_KEYS) return dumpClassic(session, info, phase)
        return when (val read = readMemory.execute(session) { reported -> if (reported != OperationPhase.CONNECTING) phase(reported) }) {
            is Outcome.Err -> read
            is Outcome.Ok -> Outcome.Ok(DumpReport(session.info, read.value))
        }
    }

    /** MIFARE Classic: authenticate each sector with the public keys only and read it. Never writes. */
    private suspend fun dumpClassic(session: TagSession, info: TagInfo, phase: (OperationPhase) -> Unit): TagResult<DumpReport> {
        val classic = SafeClassic.of(session) ?: return Outcome.Err(TagError.Unsupported(TagError.Unsupported.MIFARE_CLASSIC))
        phase(OperationPhase.READING)
        return when (val image = ClassicReader.read(classic)) {
            is Outcome.Err -> image
            is Outcome.Ok -> Outcome.Ok(DumpReport(info, image.value.toMemoryDump()))
        }
    }

    /** NfcV only: Get System Info, then Read Single Block per block. No platform NDEF call is made in this operation. */
    private suspend fun dumpIso15693(session: TagSession, info: TagInfo, phase: (OperationPhase) -> Unit): TagResult<DumpReport> {
        val transceiver = session.transceiver(TagTech.NFC_V) ?: return Outcome.Err(TagError.Unsupported("NfcV"))
        val completed = when (val result = Iso15693.complete(info, transceiver)) {
            is Outcome.Err -> return result
            is Outcome.Ok -> result.value
        }
        phase(OperationPhase.READING)
        return when (val read = readIso15693.readBlocks(completed, transceiver)) {
            is Outcome.Err -> read
            is Outcome.Ok -> Outcome.Ok(DumpReport(completed, read.value))
        }
    }

    private companion object {
        const val DUMP_TIMEOUT_MS = 60_000L
    }
}
