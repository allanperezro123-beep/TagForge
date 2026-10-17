package com.tagforge.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.theme.extendedColors

/** What the sheet shows. Text is resolved by the caller so this stays a stateless, previewable component. */
sealed interface ScanSheetState {
    data class Waiting(val title: String, val hint: String) : ScanSheetState
    data class Working(val title: String, val hint: String) : ScanSheetState
    data class Success(val title: String) : ScanSheetState
    data class Failure(val title: String, val message: String, val canRetry: Boolean = true) : ScanSheetState
}

/**
 * Bottom sheet used by every operation that needs a tag (write, erase, clone...): radar while waiting, a faster radar
 * while working, then a result. The status text is a polite live region so TalkBack announces each change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSheet(
    state: ScanSheetState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                is ScanSheetState.Waiting -> {
                    RadarPulse()
                    StatusText(state.title, state.hint)
                }
                is ScanSheetState.Working -> {
                    RadarPulse(fast = true)
                    StatusText(state.title, state.hint)
                }
                is ScanSheetState.Success -> {
                    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.extendedColors.success)
                    StatusText(state.title, null)
                }
                is ScanSheetState.Failure -> {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    StatusText(state.title, state.message)
                    if (state.canRetry) {
                        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    }
}

/** Title plus optional supporting text, announced politely when it changes. */
@Composable
fun StatusText(title: String, supporting: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
