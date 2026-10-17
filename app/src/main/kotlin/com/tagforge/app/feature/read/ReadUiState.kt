package com.tagforge.app.feature.read

import com.tagforge.domain.read.TagScan
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.task.TaskError
import com.tagforge.domain.task.TaskPayload
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagInfo

/** Everything the Read screen can show. The screen renders this and nothing else. */
sealed interface ReadUiState {
    /** No NFC adapter: explain, never crash (AS-02). */
    data object Unsupported : ReadUiState

    /** Adapter present but switched off: offer the NFC settings. */
    data object Disabled : ReadUiState

    data object Waiting : ReadUiState

    data class Reading(val phase: OperationPhase) : ReadUiState

    /** [task] is set when the tag carries a Tag Forge task: the screen then offers a Run button (never runs by itself). */
    data class Success(val scan: TagScan, val task: TaskOnTag? = null) : ReadUiState

    /** [tag] is whatever was learned before the failure (null if the tag never answered). */
    data class Error(val error: TagError, val tag: TagInfo?) : ReadUiState

    /** Password-protected NTAG: identity from pages 0-3 plus an Unlock action (TS-06). */
    data class Protected(val tag: TagInfo?) : ReadUiState
}

/** A Tag Forge task found on a scanned tag. [Invalid] tags are shown with the reason and cannot be run. */
sealed interface TaskOnTag {
    data class Valid(val payload: TaskPayload) : TaskOnTag
    data class Invalid(val error: TaskError) : TaskOnTag
}

/** One-off signals the screen turns into haptics and snackbars. */
sealed interface ReadEvent {
    data object TagDetected : ReadEvent
    data object Succeeded : ReadEvent
    data object Failed : ReadEvent
    data object UnlockUnavailable : ReadEvent

    /** The user pressed Run on a task card: open the task runner (confirmation sheet) with these NDEF bytes. */
    data class OpenTaskRunner(val ndef: Bytes) : ReadEvent
}
