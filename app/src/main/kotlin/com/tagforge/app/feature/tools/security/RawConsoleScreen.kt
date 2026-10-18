package com.tagforge.app.feature.tools.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.feature.tools.ToolScanSheet
import com.tagforge.app.ui.components.BackNavigationIcon
import com.tagforge.app.ui.components.ConfirmDialog
import com.tagforge.app.ui.components.NfcStatusBanner
import com.tagforge.app.ui.components.TypedConfirmationDialog

data class RawConsoleActions(
    val onBack: () -> Unit,
    val onHexChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onAcknowledge: () -> Unit,
    val onLightConfirm: () -> Unit,
    val onTypedChange: (String) -> Unit,
    val onTypedConfirm: () -> Unit,
    val onCancelPending: () -> Unit,
    val onSheetDismissed: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenNfcSettings: () -> Unit,
)

/** The raw console (NS-08). Presentational: warning, hex input, log, and the confirmations a frame may need. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawConsoleScreen(state: RawConsoleUiState, actions: RawConsoleActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.console_title)) }, navigationIcon = { BackNavigationIcon(actions.onBack) }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NfcStatusBanner(state.availability, actions.onOpenNfcSettings)
            if (!state.advanced) {
                Text(stringResource(R.string.console_advanced_off), color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                value = state.hexInput,
                onValueChange = actions.onHexChange,
                label = { Text(stringResource(R.string.console_input)) },
                isError = state.hexError,
                supportingText = if (state.hexError) ({ Text(stringResource(R.string.console_input_error)) }) else null,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.advanced,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )
            Button(onClick = actions.onSend, enabled = state.canSend && !state.needsAck, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.console_send))
            }
            ConsoleLog(state.log, Modifier.weight(1f))
        }
    }
    ConsoleDialogs(state, actions)
}

@Composable
private fun ConsoleLog(log: List<ConsoleEntry>, modifier: Modifier) {
    Column(modifier) {
        Text(stringResource(R.string.console_log_title), style = MaterialTheme.typography.titleSmall)
        if (log.isEmpty()) {
            Text(stringResource(R.string.console_log_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(log.asReversed()) { entry ->
                    Column {
                        Text(entry.frameHex, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                        Text(entry.outcome.text(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** The warning, the lightweight confirm, the typed UID dialog and the scan sheet. */
@Composable
private fun ConsoleDialogs(state: RawConsoleUiState, actions: RawConsoleActions) {
    if (state.advanced && state.needsAck) {
        AlertDialog(
            onDismissRequest = actions.onBack,
            title = { Text(stringResource(R.string.console_warning_title)) },
            text = { Text(stringResource(R.string.console_warning_body)) },
            confirmButton = { TextButton(onClick = actions.onAcknowledge) { Text(stringResource(R.string.console_warning_ack)) } },
            dismissButton = { TextButton(onClick = actions.onBack) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    when (val pending = state.pending) {
        is ConsolePending.Light -> ConfirmDialog(
            title = stringResource(R.string.console_light_title),
            body = stringResource(R.string.console_light_body, pending.frame.toHex(" ")),
            confirmLabel = stringResource(R.string.console_light_confirm),
            onConfirm = actions.onLightConfirm,
            onDismiss = actions.onCancelPending,
            destructive = true,
        )
        is ConsolePending.Typed -> TypedConfirmationDialog(
            plan = pending.plan,
            typed = pending.typed,
            understood = true,
            needsUnderstood = false,
            canConfirm = state.canConfirmTyped,
            onTypedChange = actions.onTypedChange,
            onUnderstoodChange = {},
            onConfirm = actions.onTypedConfirm,
            onDismiss = actions.onCancelPending,
        )
        is ConsolePending.Unplannable -> ConfirmDialog(
            title = stringResource(R.string.console_unplannable_title),
            body = stringResource(R.string.console_unplannable_body),
            confirmLabel = stringResource(R.string.common_close),
            onConfirm = actions.onCancelPending,
            onDismiss = actions.onCancelPending,
        )
        null -> Unit
    }
    state.sheet?.let { ToolScanSheet(ui = it.toUi(), onDismiss = actions.onSheetDismissed, onRetry = actions.onRetry) }
}
