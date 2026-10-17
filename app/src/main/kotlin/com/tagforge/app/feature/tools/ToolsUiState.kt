package com.tagforge.app.feature.tools

import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.tag.TagError

/** The two tools that overwrite what is on a tag. Each one asks for confirmation BEFORE it arms (G5). */
enum class DestructiveTool { ERASE, FORMAT }

/** What the scan sheet shows while a destructive tool is armed or running. */
sealed interface ToolSheet {
    val tool: DestructiveTool

    data class Waiting(override val tool: DestructiveTool) : ToolSheet
    data class Working(override val tool: DestructiveTool, val phase: OperationPhase) : ToolSheet

    /** [changed] is false when the tag already was empty/clean and nothing was written. */
    data class Success(override val tool: DestructiveTool, val changed: Boolean) : ToolSheet

    /** [canRetry] is false when trying again cannot help (locked tag, unsupported card). */
    data class Failure(override val tool: DestructiveTool, val error: TagError, val canRetry: Boolean) : ToolSheet
}

/** One-off signals the screen turns into haptics. */
sealed interface ToolsEvent {
    data object TagDetected : ToolsEvent
    data object Succeeded : ToolsEvent
    data object Failed : ToolsEvent
}

data class ToolsUiState(
    val availability: NfcAvailability = NfcAvailability.ENABLED,
    /** The tool whose confirmation dialog is open; nothing is armed while this is set. */
    val confirm: DestructiveTool? = null,
    val sheet: ToolSheet? = null,
) {
    val canStart: Boolean get() = availability == NfcAvailability.ENABLED
}
