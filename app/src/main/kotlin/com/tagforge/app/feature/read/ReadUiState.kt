package com.tagforge.app.feature.read

import com.tagforge.domain.read.TagScan
import com.tagforge.domain.scan.OperationPhase
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

    data class Success(val scan: TagScan) : ReadUiState

    /** [tag] is whatever was learned before the failure (null if the tag never answered). */
    data class Error(val error: TagError, val tag: TagInfo?) : ReadUiState

    /** Password-protected NTAG: identity from pages 0-3 plus an Unlock action (TS-06). */
    data class Protected(val tag: TagInfo?) : ReadUiState
}

/** One-off signals the screen turns into haptics and snackbars. */
sealed interface ReadEvent {
    data object TagDetected : ReadEvent
    data object Succeeded : ReadEvent
    data object Failed : ReadEvent
    data object UnlockUnavailable : ReadEvent
}
