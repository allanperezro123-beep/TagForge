package com.tagforge.domain.exchange

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.spec.RecordSpec

/** [remaining] counts numbers not yet written or skipped; [failed] counts failed ATTEMPTS (a number can fail several times). */
data class BatchSummary(val ok: Int, val failed: Int, val remaining: Int, val skipped: Int = 0)

/**
 * Progress of a batch. Immutable: each event returns the next state. The counter advances ONLY on
 * [recordSuccess], which the caller invokes after the write was read back and verified, or on [recordSkip], an explicit
 * decision of the user. A failed attempt (tag moved away, locked, verification mismatch) leaves the counter where it
 * was, so the next tag gets the same number.
 */
data class BatchRun(
    val plan: BatchPlan,
    val completed: Int = 0,
    val failedAttempts: Int = 0,
    val skipped: Int = 0,
    private val writtenUids: Set<Bytes> = emptySet(),
) {
    /** How many numbers have been used up: written or skipped. */
    private val position: Int get() = completed + skipped

    val isComplete: Boolean get() = position >= plan.settings.total

    /** The number the next tag will carry. */
    val nextCounter: Long get() = plan.counterAt(position)

    val nextLabel: String get() = plan.label(position)

    val summary: BatchSummary
        get() = BatchSummary(
            ok = completed,
            failed = failedAttempts,
            remaining = (plan.settings.total - position).coerceAtLeast(0),
            skipped = skipped,
        )

    /** Records for the next tag, or empty when the batch is complete. */
    fun nextRecords(): List<RecordSpec> = if (isComplete) emptyList() else plan.render(position)

    /** The message for the next tag (AAR included when the plan asks for it), or null when the batch is complete. */
    fun nextMessage(appPackage: String): NdefMessage? = if (isComplete) null else plan.messageAt(position, appPackage)

    /** The same physical tag twice in one batch is almost always a mistake: ask first (G5). */
    fun needsOverwriteConfirmation(uid: Bytes): Boolean = uid in writtenUids

    fun recordSuccess(uid: Bytes): BatchRun =
        if (isComplete) this else copy(completed = completed + 1, writtenUids = writtenUids + uid)

    fun recordFailure(): BatchRun = copy(failedAttempts = failedAttempts + 1)

    /** Gives up on the current number (e.g. a bad tag) and moves on to the next. */
    fun recordSkip(): BatchRun = if (isComplete) this else copy(skipped = skipped + 1)
}
