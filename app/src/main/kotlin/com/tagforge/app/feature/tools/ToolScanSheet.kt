package com.tagforge.app.feature.tools

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
import com.tagforge.app.ui.theme.Motion
import com.tagforge.app.ui.theme.extendedColors
import com.tagforge.app.ui.theme.rememberReducedMotion

private const val CHECK_START_SCALE = 0.3f

/** What the sheet shows, with every string already resolved by the container so this stays stateless. */
sealed interface ToolSheetUi {
    data class Waiting(val title: String, val hint: String) : ToolSheetUi
    data class Working(val title: String, val hint: String) : ToolSheetUi
    data class Success(val title: String, val detail: String?, val extraLabel: String? = null) : ToolSheetUi
    data class Failure(val title: String, val message: String, val hint: String?, val canRetry: Boolean) : ToolSheetUi
}

/**
 * The sheet that guides a tool from "hold the tag" to a result. While the tag is being changed it cannot be dismissed
 * (swipe, scrim tap and back are all ignored): pulling it away would abort the operation half way. Status text is a
 * polite live region, so TalkBack announces every change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScanSheet(
    ui: ToolSheetUi,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onExtra: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val working = ui is ToolSheetUi.Working
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !working })
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (ui) {
                is ToolSheetUi.Waiting -> {
                    RadarPulse()
                    StatusText(ui.title, ui.hint)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                }
                is ToolSheetUi.Working -> {
                    RadarPulse(fast = true)
                    StatusText(ui.title, ui.hint)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is ToolSheetUi.Success -> SuccessContent(ui, onDismiss, onExtra)
                is ToolSheetUi.Failure -> FailureContent(ui, onDismiss, onRetry)
            }
        }
    }
}

@Composable
private fun SuccessContent(ui: ToolSheetUi.Success, onDone: () -> Unit, onExtra: () -> Unit) {
    val reduced = rememberReducedMotion()
    val scale = remember { Animatable(if (reduced) 1f else CHECK_START_SCALE) }
    LaunchedEffect(Unit) {
        if (!reduced) scale.animateTo(1f, tween(Motion.ConfirmMillis, easing = Motion.Overshoot))
    }
    Icon(
        Icons.Outlined.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.extendedColors.success,
        modifier = Modifier.size(72.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
    )
    StatusText(ui.title, ui.detail)
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.tools_sheet_done)) }
    if (ui.extraLabel != null) {
        OutlinedButton(onClick = onExtra, modifier = Modifier.fillMaxWidth()) { Text(ui.extraLabel) }
    }
}

@Composable
private fun FailureContent(ui: ToolSheetUi.Failure, onClose: () -> Unit, onRetry: () -> Unit) {
    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(72.dp))
    StatusText(ui.title, ui.message)
    ui.hint?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (ui.canRetry) {
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_retry)) }
        TextButton(onClick = onClose) { Text(stringResource(R.string.common_cancel)) }
    } else {
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.tools_sheet_done)) }
    }
}
