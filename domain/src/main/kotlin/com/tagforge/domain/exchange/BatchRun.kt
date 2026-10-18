package com.tagforge.domain.exchange

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ndef.spec.RecordSpec

data class BatchSummary(val ok: Int, val failed: Int, val remaining: Int)

/**
 * Progress of a batch. Immutable: each event returns the next state. The counter advances ONLY on
 * [recordSuccess], which the caller invokes after the write was read back and verified; a failed attempt (tag moved
 * away, locked, verification mismatch) leaves it where it was, so the next tag gets the same number.
 */
data class BatchRun(
    val plan: BatchPlan,
    val completed: Int = 0,
    val failedAttempts: Int = 0,
    private val writtenUids: Set<Bytes> = emptySet(),
) {
    val isComplete: Boolean get() = completed >= plan.settings.total

    /** The number the next tag will carry. */
    val nextCounter: Long get() = plan.counterAt(completed)

    val nextLabel: String get() = plan.label(completed)

    val summary: BatchSummary
        get() = BatchSummary(ok = completed, failed = failedAttempts, remaining = (plan.settings.total - completed).coerceAtLeast(0))

    /** Records for the next tag, or empty when the batch is complete. */
    fun nextRecords(): List<RecordSpec> = if (isComplete) emptyList() else plan.render(completed)

    /** The same physical tag twice in one batch is almost always a mistake: ask first (G5). */
    fun needsOverwriteConfirmation(uid: Bytes): Boolean = uid in writtenUids

    fun recordSuccess(uid: Bytes): BatchRun =
        if (isComplete) this else copy(completed = completed + 1, writtenUids = writtenUids + uid)

    fun recordFailure(): BatchRun = copy(failedAttempts = failedAttempts + 1)
}
