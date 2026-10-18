package com.tagforge.domain.exchange

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.write.WriteNdefUseCase
import com.tagforge.domain.write.WriteReport

/**
 * One tag of a batch: a verified [WriteNdefUseCase] plus the batch's own safety check. A tag whose UID was already
 * written in this batch ([alreadyWritten]) is almost certainly the previous tag presented again, so the user must
 * confirm before it is rewritten (G5); declining is `Cancelled` with zero writes. Any other tag that already holds
 * data goes through the usual overwrite question.
 *
 * @param confirm answers "replace this?": [existing] is what the tag holds now, [duplicate] says it was written in this batch
 */
class BatchWriteOperation(
    private val message: NdefMessage,
    private val alreadyWritten: (Bytes) -> Boolean,
    private val confirm: suspend (existing: NdefMessage?, duplicate: Boolean) -> Boolean,
) : TagOperation<WriteReport> {

    override val kind: OperationKind = OperationKind.WRITE

    /** Same allowance as a single write: the user may be reading a confirmation. */
    override val timeoutMs: Long = CONFIRMATION_TIMEOUT_MS

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<WriteReport> {
        var duplicateConfirmed = false
        if (alreadyWritten(session.info.uid)) {
            if (!confirm(null, true)) return Outcome.Err(TagError.Cancelled)
            duplicateConfirmed = true
        }
        return WriteNdefUseCase(message) { existing -> duplicateConfirmed || confirm(existing, false) }.execute(session, phase)
    }

    private companion object {
        const val CONFIRMATION_TIMEOUT_MS = 60_000L
    }
}
