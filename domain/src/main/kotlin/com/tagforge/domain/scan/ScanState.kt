package com.tagforge.domain.scan

import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagInfo

sealed interface ScanState<out T> {
    data object Idle : ScanState<Nothing>

    data class Waiting(val kind: OperationKind) : ScanState<Nothing>

    data class Working(val kind: OperationKind, val phase: OperationPhase) : ScanState<Nothing>

    data class Success<out T>(val kind: OperationKind, val tag: TagInfo, val value: T) : ScanState<T>

    /** [tag] is whatever was learned before the failure (null when the operation never reached a tag). */
    data class Failure(val kind: OperationKind, val tag: TagInfo?, val error: TagError) : ScanState<Nothing>
}
