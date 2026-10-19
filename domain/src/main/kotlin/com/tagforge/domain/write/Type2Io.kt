package com.tagforge.domain.write

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagResponses
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.type2.Type2Chip
import com.tagforge.domain.type2.Type2Layout

/**
 * The raw page I/O shared by every operation that edits the NDEF area of an NTAG21x (write, erase, format, clone):
 * reading the capability container plus user memory, and running a planned list of page writes safely.
 */
internal object Type2Io {

    const val CC_PAGE = 3
    const val CC_MAGIC = 0xE1
    private const val READ_CHUNK_PAGES = 4
    const val CC_SIZE_UNIT = 8

    /**
     * Page 3 (capability container) followed by the user pages, read in small chunks: FAST_READ where the chip has it
     * (every NTAG21x, exactly as before), plain READ (16 bytes, trimmed) for MIFARE Ultralight and Ultralight C.
     */
    suspend fun readCcAndUserArea(transceiver: TagTransceiver, layout: Type2Layout): TagResult<CcAndUserArea> {
        val chunks = ArrayList<Bytes>()
        var page = CC_PAGE
        while (page <= layout.userEnd) {
            val end = minOf(page + READ_CHUNK_PAGES - 1, layout.userEnd)
            when (val chunk = readPages(transceiver, layout, page, end)) {
                is Outcome.Err -> return Outcome.Err(if (chunk.error is TagError.Nak) TagError.AuthRequired else chunk.error)
                is Outcome.Ok -> chunks += chunk.value
            }
            page = end + 1
        }
        return Outcome.Ok(CcAndUserArea(Bytes.concat(chunks)))
    }

    /** Pages [start]..[end] (at most four) as exactly `(end - start + 1) * 4` bytes. */
    suspend fun readPages(transceiver: TagTransceiver, layout: Type2Layout, start: Int, end: Int): TagResult<Bytes> {
        val wanted = (end - start + 1) * NtagCommands.PAGE_SIZE
        val command = if (layout.supportsFastRead) NtagCommands.fastRead(start, end) else NtagCommands.read(start)
        val response = when (val sent = transceiver.transceive(command)) {
            is Outcome.Err -> return sent
            is Outcome.Ok -> sent.value
        }
        val expected = if (layout.supportsFastRead) wanted else NtagCommands.READ_RESPONSE_SIZE
        val data = when (val checked = NtagResponses.checkData(response, expected)) {
            is Outcome.Err -> return checked
            is Outcome.Ok -> checked.value
        }
        return Outcome.Ok(if (data.size == wanted) data else data.slice(0, wanted))
    }

    /**
     * A MIFARE Ultralight family tag or NTAG210/212 whose page 3 (OTP) is still all zero has no capability container
     * yet: writes the NFC Forum one (`E1 10 xx 00`) so the tag becomes an NDEF tag WITHOUT leaving raw NfcA, the same
     * thing Android's NdefFormatable does after switching technology. OTP bits only ever go from 0 to 1, so this is
     * possible exactly once and only on a blank page 3. Returns the area as it reads afterwards, or null when the tag
     * cannot be initialised this way (not such a chip, or page 3 is already used by something else).
     */
    suspend fun initCapabilityContainer(transceiver: TagTransceiver, layout: Type2Layout, area: CcAndUserArea): TagResult<CcAndUserArea?> {
        val chip = layout as? Type2Chip ?: return Outcome.Ok(null)
        val blank = (0 until NtagCommands.PAGE_SIZE).all { area.bytes[it] == 0 }
        if (!blank) return Outcome.Ok(null)
        val written = execute(transceiver, listOf(PageWrite(CC_PAGE, chip.blankCapabilityContainer)))
        if (written is Outcome.Err) return written
        return Outcome.Ok(CcAndUserArea(chip.blankCapabilityContainer + area.user))
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

    /**
     * NDEF area size in bytes for [layout]: the size the capability container advertises when the chip is one whose CC
     * is the authority (and the value is plausible), the datasheet value otherwise (every NTAG213/215/216).
     */
    fun ndefAreaFor(layout: Type2Layout): Int {
        val advertised = bytes[2] * Type2Io.CC_SIZE_UNIT
        val userBytes = (layout.userEnd - layout.userStart + 1) * NtagCommands.PAGE_SIZE
        return if (layout.trustsCcSize && hasCapabilityContainer && advertised in 1..userBytes) advertised else layout.ccNdefSize
    }

    /** The CC says the tag was made read-only (write access byte is not 00). */
    val isReadOnlyPerCc: Boolean get() = bytes[3] != 0
}
