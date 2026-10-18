package com.tagforge.app.feature.tasks

import com.tagforge.app.feature.write.WriteTarget
import com.tagforge.domain.task.TaskAction

/** Every callback the task builder can raise, bundled so the presentational composables stay readable. */
data class TaskBuilderActions(
    val onNameChanged: (String) -> Unit,
    val onAddAction: () -> Unit,
    val onDismissPicker: () -> Unit,
    val onKindSelected: (ActionKind) -> Unit,
    val onActionChanged: (TaskAction) -> Unit,
    val onConfirmAction: () -> Unit,
    val onCancelEditing: () -> Unit,
    val onEditAction: (Int) -> Unit,
    val onRemoveAction: (Int) -> Unit,
    val onMoveAction: (Int, Int) -> Unit,
    val onTargetSelected: (WriteTarget) -> Unit,
    val onSave: () -> Unit,
    val onWrite: () -> Unit,
    val onRetry: () -> Unit,
    val onOverwriteDecision: (Boolean) -> Unit,
    val onSheetDismissed: () -> Unit,
    val onOpenNfcSettings: () -> Unit,
)
