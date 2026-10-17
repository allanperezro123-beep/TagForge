package com.tagforge.domain.write

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ntag.NtagResponses
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.tag.TagError

/**
 * The raw page I/O shared by every operation that edits the NDEF area of an NTAG21x (write, erase, format, clone):
 * reading the capability container plus user memory, and running a planned list of page writes safely.
 */
internal object Type2Io {

    const val CC_PAGE = 3
    const val CC_MAGIC = 0xE1
    private const val READ_CHUNK_PAGES = 4

    /** Page 3 (capability container) followed by the user pages, read in small FAST_READ chunks. */
    suspend fun readCcAndUserArea(transceiver: TagTransceiver, model: NtagModel): TagResult<CcAndUserArea> {
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
        return Outcome.Ok(CcAndUserArea(Bytes.concat(chunks)))
    }

    /**
     * Runs [steps] in order. A refusal on the very first write means the tag is write-protected (nothing changed yet),
     * so it becomes [TagError.ReadOnly]. Some NFC controllers swallow the 4-bit ACK and return nothing: that counts as
     * acknowledged, because every caller proves the result by reading the tag back.
     */
    suspend fun execute(transceiver: TagTransceiver, steps: List<PageWrite>): TagResult<Unit> {
        for ((index, step) in steps.withIndex()) {
            val response = when (val sent = transceiver.transceive(NtagCommands.write(step.page, step.data))) {
                is Outcome.Err -> return sent
                is Outcome.Ok -> sent.value
            }
            val acknowledged = if (response.isEmpty()) Outcome.Ok(Unit) else NtagResponses.checkAck(response)
            if (acknowledged is Outcome.Err) {
                return if (index == 0 && acknowledged.error is TagError.Nak) Outcome.Err(TagError.ReadOnly) else acknowledged
            }
        }
        return Outcome.Ok(Unit)
    }
}

/** Page 3 followed by the user pages of an NTAG21x. */
internal class CcAndUserArea(val bytes: Bytes) {
    /** The capability container (page 3). */
    val cc: Bytes get() = bytes.slice(0, NtagCommands.PAGE_SIZE)

    /** The user pages, from page 4 on. */
    val user: Bytes get() = bytes.slice(NtagCommands.PAGE_SIZE)

    /** False when page 3 does not start with the NFC Forum magic `E1`: the tag has no capability container. */
    val hasCapabilityContainer: Boolean get() = bytes[0] == Type2Io.CC_MAGIC

    /** The CC says the tag was made read-only (write access byte is not 00). */
    val isReadOnlyPerCc: Boolean get() = bytes[3] != 0
}
