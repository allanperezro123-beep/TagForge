package com.tagforge.domain.read

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.MemoryDump
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagResponses
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagTech

/**
 * Reads the raw pages of a Type 2 tag for the hex view.
 *
 * NTAG21x: FAST_READ in 4-page chunks (a chunk touching a protected page is NAKed as a whole, so no protected
 * data can leak and the cut is at most 3 pages early). Unknown chips: the first 16 pages with plain READ, which
 * every Type 2 tag supports.
 */
class ReadMemoryUseCase : TagOperation<MemoryDump> {

    override val kind: OperationKind = OperationKind.MEMORY_DUMP

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<MemoryDump> {
        phase(OperationPhase.CONNECTING)
        val info = when (val identified = session.identify()) {
            is Outcome.Err -> return identified
            is Outcome.Ok -> identified.value
        }
        val transceiver = session.transceiver(TagTech.NFC_A) ?: return Outcome.Err(TagError.Unsupported("NfcA"))

        phase(OperationPhase.READING)
        val model = info.ntag
        val totalPages = model?.totalPages ?: UNKNOWN_CHIP_PAGES
        val pages = ArrayList<Bytes>(totalPages)

        var start = 0
        while (start < totalPages) {
            val end = minOf(start + CHUNK_PAGES - 1, totalPages - 1)
            val command = if (model != null) NtagCommands.fastRead(start, end) else NtagCommands.read(start)
            val response = when (val sent = transceiver.transceive(command)) {
                is Outcome.Err -> return sent
                is Outcome.Ok -> sent.value
            }
            val expectedBytes = (end - start + 1) * NtagCommands.PAGE_SIZE
            val data = when (val checked = NtagResponses.checkData(response, expectedBytes)) {
                is Outcome.Ok -> checked.value
                is Outcome.Err ->
                    if (checked.error is TagError.Nak && start > 0) {
                        return Outcome.Ok(MemoryDump(model, pages, start until totalPages))
                    } else {
                        return checked
                    }
            }
            for (i in 0 until (end - start + 1)) pages += data.slice(i * NtagCommands.PAGE_SIZE, (i + 1) * NtagCommands.PAGE_SIZE)
            start = end + 1
        }
        return Outcome.Ok(MemoryDump(model, pages, null))
    }

    private companion object {
        const val CHUNK_PAGES = 4
        const val UNKNOWN_CHIP_PAGES = 16
    }
}
