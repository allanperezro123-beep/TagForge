package com.tagforge.app.feature.write

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tagforge.app.R

/** The name shown for the tag the meter measures against. */
@Composable
fun targetName(target: WriteTarget): String = when (target) {
    is WriteTarget.Chip -> target.model.displayName
    is WriteTarget.LastTag -> stringResource(R.string.write_target_detected, target.maxSize)
}

/** Every callback the Write screen can raise. Bundled so the presentational composables stay readable. */
data class WriteActions(
    val onTypeSelected: (RecordKind) -> Unit,
    val onDraftChanged: (com.tagforge.domain.ndef.spec.RecordSpec) -> Unit,
    val onFieldTouched: (com.tagforge.domain.ndef.spec.SpecField) -> Unit,
    val onContinue: () -> Unit,
    val onAddRecord: () -> Unit,
    val onEditRecord: (Int) -> Unit,
    val onRemoveRecord: (Int) -> Unit,
    val onMoveRecord: (Int, Int) -> Unit,
    val onIncludeAarChanged: (Boolean) -> Unit,
    val onTargetSelected: (WriteTarget) -> Unit,
    val onWrite: () -> Unit,
    val onSaveTemplate: () -> Unit,
    val onOverwriteDecision: (Boolean) -> Unit,
    val onRetry: () -> Unit,
    val onWriteAnother: () -> Unit,
    val onSheetDismissed: () -> Unit,
    val onUndoRemove: () -> Unit,
    val onOpenNfcSettings: () -> Unit,
)
