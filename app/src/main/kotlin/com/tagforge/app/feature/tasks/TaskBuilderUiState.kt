package com.tagforge.app.feature.tasks

import com.tagforge.app.feature.write.OverwritePrompt
import com.tagforge.app.feature.write.WriteSheet
import com.tagforge.app.feature.write.WriteTarget
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.task.ActionCheck
import com.tagforge.domain.task.ActionValidator
import com.tagforge.domain.task.BlockReason
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.task.TaskAnalysis
import com.tagforge.domain.task.TaskLimits

/**
 * The action whose form is open. [index] is the position being edited, null for a new action. Errors show only after
 * the user tried to add ([showAllErrors]) or once the field is no longer empty, so a fresh form is not shouting.
 */
data class EditingAction(
    val index: Int?,
    val action: TaskAction,
    val showAllErrors: Boolean = false,
) {
    val check: ActionCheck get() = ActionValidator.check(action)
    val blockReason: BlockReason? get() = (check as? ActionCheck.Blocked)?.reason
    val isValid: Boolean get() = check !is ActionCheck.Blocked
}

/** One-off signals the screen turns into haptics and snackbars. */
sealed interface TaskBuilderEvent {
    data object TagDetected : TaskBuilderEvent
    data object WriteSucceeded : TaskBuilderEvent
    data object WriteFailed : TaskBuilderEvent
    data class Saved(val name: String) : TaskBuilderEvent

    /** An action was just removed: the screen offers Undo for a few seconds. */
    data object ActionRemoved : TaskBuilderEvent
}

/** Everything the task builder shows. The derived fields are recomputed by the ViewModel on every change. */
data class TaskBuilderUiState(
    val availability: NfcAvailability = NfcAvailability.ENABLED,
    val loading: Boolean = false,
    val taskId: Long? = null,
    val name: String = "",
    val nameError: Boolean = false,
    val actions: List<TaskAction> = emptyList(),
    val picking: Boolean = false,
    val editing: EditingAction? = null,
    val target: WriteTarget = WriteTarget.Chip(NtagModel.NTAG213),
    val sheet: WriteSheet? = null,
    val overwrite: OverwritePrompt? = null,
    val canUndoRemove: Boolean = false,
    // ---- derived ----
    val capacity: Int = 0,
    val analysis: TaskAnalysis,
    val canWrite: Boolean = false,
    val canSave: Boolean = false,
) {
    val isFull: Boolean get() = actions.size >= TaskLimits.MAX_ACTIONS
}
