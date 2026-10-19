package com.tagforge.app.feature.write

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.components.RadarPulse
import com.tagforge.app.ui.components.StatusText
import com.tagforge.app.ui.text.TagErrorText
import com.tagforge.app.ui.text.messageText
import com.tagforge.app.ui.text.titleText
import com.tagforge.app.ui.theme.Motion
import com.tagforge.app.ui.theme.extendedColors
import com.tagforge.app.ui.theme.rememberReducedMotion
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.tag.TagError

private const val SHAKE_DISTANCE_PX = 18f
private const val SHAKE_STEP_MILLIS = 60
private const val CHECK_START_SCALE = 0.3f

/**
 * The sheet that guides a write from "hold the tag" to a verified result. Dismissal is blocked while the tag is being
 * written: pulling the sheet away would abort the operation half way. The status text is a polite live region.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteScanSheet(
    sheet: WriteSheet,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onWriteAnother: (() -> Unit)?,
    onSaveTemplate: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val working = sheet is WriteSheet.Working
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !working },
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (sheet) {
                is WriteSheet.Waiting -> {
                    RadarPulse()
                    StatusText(
                        stringResource(R.string.write_sheet_waiting_title),
                        stringResource(R.string.write_sheet_waiting_hint, sheet.messageSize),
                    )
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                }
                is WriteSheet.Working -> {
                    RadarPulse(fast = true)
                    StatusText(stringResource(phaseTitle(sheet.phase)), stringResource(R.string.write_sheet_keep_still))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is WriteSheet.Success -> SuccessContent(sheet, onDismiss, onWriteAnother, onSaveTemplate)
                is WriteSheet.Failure -> FailureContent(sheet, onDismiss, onRetry)
            }
        }
    }
}

private fun phaseTitle(phase: OperationPhase): Int = when (phase) {
    OperationPhase.CONNECTING, OperationPhase.READING -> R.string.write_sheet_connecting
    OperationPhase.WRITING -> R.string.write_sheet_writing
    OperationPhase.VERIFYING -> R.string.write_sheet_verifying
}

@Composable
private fun SuccessContent(
    sheet: WriteSheet.Success,
    onDone: () -> Unit,
    onWriteAnother: (() -> Unit)?,
    onSaveTemplate: (() -> Unit)?,
) {
    val reduced = rememberReducedMotion()
    val scale = remember { Animatable(if (reduced) 1f else CHECK_START_SCALE) }
    LaunchedEffect(Unit) {
        if (!reduced) scale.animateTo(1f, tween(Motion.ConfirmMillis, easing = Motion.Overshoot))
    }
    val info = sheet.report.info
    Icon(
        Icons.Outlined.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.extendedColors.success,
        modifier = Modifier.size(72.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
    )
    StatusText(
        stringResource(R.string.write_sheet_success),
        stringResource(R.string.write_sheet_success_detail, sheet.report.messageSize, info.chipName ?: "NFC"),
    )
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.write_sheet_done)) }
    if (onWriteAnother != null) {
        OutlinedButton(onClick = onWriteAnother, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.write_sheet_again)) }
    }
    if (onSaveTemplate != null) TextButton(onClick = onSaveTemplate) { Text(stringResource(R.string.write_save_template)) }
}

@Composable
private fun FailureContent(sheet: WriteSheet.Failure, onClose: () -> Unit, onRetry: () -> Unit) {
    val reduced = rememberReducedMotion()
    val shake = remember { Animatable(0f) }
    LaunchedEffect(sheet.error) {
        if (!reduced) {
            for (target in listOf(SHAKE_DISTANCE_PX, -SHAKE_DISTANCE_PX, SHAKE_DISTANCE_PX / 2, 0f)) {
                shake.animateTo(target, tween(SHAKE_STEP_MILLIS))
            }
        }
    }
    val text = TagErrorText.of(sheet.error)
    Icon(
        Icons.Outlined.ErrorOutline,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(72.dp).graphicsLayer { translationX = shake.value },
    )
    StatusText(text.titleText() ?: stringResource(R.string.write_sheet_failed), text.messageText())
    failureHint(sheet.error)?.let { hint ->
        Text(
            stringResource(hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (sheet.canRetry) {
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_retry)) }
        TextButton(onClick = onClose) { Text(stringResource(R.string.common_cancel)) }
    } else {
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.write_sheet_done)) }
    }
}

/** Extra guidance for the failures where "try again" cannot help. */
private fun failureHint(error: TagError): Int? = when (error) {
    TagError.ReadOnly -> R.string.write_sheet_locked_hint
    is TagError.CapacityExceeded -> R.string.write_sheet_change_target
    else -> null
}
