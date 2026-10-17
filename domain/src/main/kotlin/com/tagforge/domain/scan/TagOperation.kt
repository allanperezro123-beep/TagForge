package com.tagforge.domain.scan

import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ports.TagSession

enum class OperationPhase { CONNECTING, READING, WRITING, VERIFYING }

/** One thing to do to the next tag that appears (read it, write it, ...). */
interface TagOperation<out T> {
    val kind: OperationKind

    /** Longer (or shorter) than the coordinator default when the operation waits on the user, e.g. an overwrite confirmation. */
    val timeoutMs: Long? get() = null

    /** Must not throw for tag failures: return Err instead. [phase] lets the UI show progress. */
    suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<T>
}
