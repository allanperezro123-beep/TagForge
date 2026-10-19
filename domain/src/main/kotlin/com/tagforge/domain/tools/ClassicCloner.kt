package com.tagforge.domain.tools

import com.tagforge.domain.classic.ClassicImage
import com.tagforge.domain.classic.ClassicReader
import com.tagforge.domain.classic.SafeClassic
import com.tagforge.domain.classic.SectorOpener
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagInfo

/**
 * The MIFARE Classic side of the two-tap clone. Copies the DATA blocks that public keys could read, and only those:
 * never block 0, never a sector trailer (enforced by [SafeClassic], not by this code), never a sector the destination
 * would not open with a public key. The destination must have the same layout. The result is a verified copy of the
 * data, not a UID clone: UID and keys of the destination stay its own.
 */
internal object ClassicCloner {

    suspend fun readSource(session: TagSession, info: TagInfo, phase: (OperationPhase) -> Unit): TagResult<CloneSource> {
        val classic = SafeClassic.of(session) ?: return Outcome.Err(TagError.Unsupported(TagError.Unsupported.MIFARE_CLASSIC))
        phase(OperationPhase.READING)
        val image = when (val read = ClassicReader.read(classic)) {
            is Outcome.Err -> return read
            is Outcome.Ok -> read.value
        }
        if (image.dataBlocks.isEmpty()) return Outcome.Err(TagError.AuthRequired)   // every sector needs a private key
        return Outcome.Ok(CloneSource(info, NdefMessage.BLANK, userPages = null, classic = image))
    }

    suspend fun writeTarget(
        session: TagSession,
        source: CloneSource,
        image: ClassicImage,
        confirmOverwrite: suspend (NdefMessage?) -> Boolean,
        phase: (OperationPhase) -> Unit,
    ): TagResult<CloneReport> {
        val classic = SafeClassic.of(session) ?: return Outcome.Err(TagError.Unsupported(TagError.Unsupported.MIFARE_CLASSIC))
        if (classic.layout != image.layout) return Outcome.Err(TagError.Unsupported(TagError.Unsupported.USER_PAGES_MODEL))
        // A Classic destination always holds something we can not summarise: ask before touching it (G5).
        if (!confirmOverwrite(null)) return Outcome.Err(TagError.Cancelled)

        phase(OperationPhase.WRITING)
        val opener = SectorOpener(classic)
        val skipped = ArrayList<Int>()
        val copied = HashMap<Int, com.tagforge.domain.common.Bytes>()
        val bySector = image.dataBlocks.entries.groupBy { image.layout.sectorOf(it.key) }.toSortedMap()
        for ((sector, entries) in bySector) {
            val opened = when (val result = opener.open(sector)) {
                is Outcome.Err -> return result
                is Outcome.Ok -> result.value
            }
            if (opened == null) {
                skipped += sector
                continue
            }
            for ((block, data) in entries.sortedBy { it.key }) {
                when (val written = classic.writeBlock(block, data)) {
                    is Outcome.Ok -> copied[block] = data
                    is Outcome.Err -> if (written.error == TagError.TagLost) return written else {
                        skipped += sector
                        break
                    }
                }
            }
        }
        if (copied.isEmpty()) return Outcome.Err(TagError.AuthRequired)

        phase(OperationPhase.VERIFYING)
        for ((sector, entries) in bySector) {
            val blocks = entries.map { it.key }.filter { it in copied }
            if (blocks.isEmpty()) continue
            if (opener.open(sector) !is Outcome.Ok) return Outcome.Err(TagError.TagLost)
            for (block in blocks) {
                val actual = when (val read = classic.readBlock(block)) {
                    is Outcome.Err -> return read
                    is Outcome.Ok -> read.value
                }
                if (actual != copied.getValue(block)) return Outcome.Err(TagError.VerifyMismatch(copied.getValue(block), actual))
            }
        }
        return Outcome.Ok(
            CloneReport(source.info, session.info, messageSize = 0, copiedUserPages = false, classicBlocksCopied = copied.size, classicSectorsSkipped = skipped.distinct()),
        )
    }
}
