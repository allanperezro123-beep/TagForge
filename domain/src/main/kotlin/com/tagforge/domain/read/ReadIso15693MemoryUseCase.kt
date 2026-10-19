package com.tagforge.domain.read

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.iso15693.Iso15693
import com.tagforge.domain.ntag.MemoryDump
import com.tagforge.domain.ntag.PageAnnotation
import com.tagforge.domain.ntag.Region
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech

/**
 * Dumps the blocks of an ISO 15693 tag with Read Single Block (20h), one NfcV frame per block and nothing else: the
 * whole operation stays on the NfcV technology. Block count and size come from Get System Info (2Bh); a tag that does
 * not answer it is probed block by block (4-byte blocks assumed) until it reports that the block does not exist.
 *
 * Single-block commands address 256 blocks at most; a bigger tag (ST25DV64K has 2048) is dumped up to that limit and the
 * rest is reported as unreadable. A refusal after the first block (locked area) ends the dump the same way.
 */
class ReadIso15693MemoryUseCase : TagOperation<MemoryDump> {

    override val kind: OperationKind = OperationKind.MEMORY_DUMP

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<MemoryDump> {
        phase(OperationPhase.CONNECTING)
        val info = when (val identified = session.identify()) {
            is Outcome.Err -> return identified
            is Outcome.Ok -> identified.value
        }
        val transceiver = session.transceiver(TagTech.NFC_V) ?: return Outcome.Err(TagError.Unsupported("NfcV"))
        val completed = when (val result = Iso15693.complete(info, transceiver)) {
            is Outcome.Err -> return result
            is Outcome.Ok -> result.value
        }
        phase(OperationPhase.READING)
        return readBlocks(completed, transceiver)
    }

    /** The block loop, for callers that already completed [info] with Get System Info. */
    suspend fun readBlocks(info: TagInfo, transceiver: TagTransceiver): TagResult<MemoryDump> {
        val declared = info.details.nfcV?.blockCount
        val limit = minOf(declared ?: Iso15693.MAX_BLOCKS_ADDRESSABLE, Iso15693.MAX_BLOCKS_ADDRESSABLE)
        val blocks = ArrayList<Bytes>(limit)

        for (block in 0 until limit) {
            val data = when (val sent = transceiver.transceive(Iso15693.readSingleBlock(info.uid, block))) {
                is Outcome.Err -> return sent
                is Outcome.Ok -> Iso15693.blockData(sent.value)
            }
            when (data) {
                is Outcome.Ok -> blocks += data.value
                is Outcome.Err -> {
                    if (block == 0) return Outcome.Err(if (data.error is TagError.Nak) TagError.AuthRequired else data.error)
                    // Probing past the last block is the normal end; a declared block that fails is "unreadable".
                    val unreadable = if (declared != null) block until declared else null
                    return Outcome.Ok(dump(blocks, unreadable))
                }
            }
        }
        val unreadable = if (declared != null && declared > limit) limit until declared else null
        return Outcome.Ok(dump(blocks, unreadable))
    }

    private fun dump(blocks: List<Bytes>, unreadable: IntRange?) = MemoryDump(null, blocks, unreadable, null, annotate(blocks))

    /** Type 5 tags keep the capability container in the first block(s): `E1` = 4-byte CC, `E2` = 8-byte CC. */
    private fun annotate(blocks: List<Bytes>): List<PageAnnotation>? {
        val first = blocks.firstOrNull()?.takeIf { !it.isEmpty() } ?: return null
        val ccBytes = when (first[0]) {
            CC_MAGIC_4 -> CC_4
            CC_MAGIC_8 -> CC_8
            else -> return null
        }
        val ccBlocks = (ccBytes + first.size - 1) / first.size
        return blocks.indices.map { PageAnnotation(it, listOf(if (it < ccBlocks) Region.CC else Region.USER)) }
    }

    private companion object {
        const val CC_MAGIC_4 = 0xE1
        const val CC_MAGIC_8 = 0xE2
        const val CC_4 = 4
        const val CC_8 = 8
    }
}
