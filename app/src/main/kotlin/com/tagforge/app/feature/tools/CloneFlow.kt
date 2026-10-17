package com.tagforge.app.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.components.BackNavigationIcon
import com.tagforge.app.ui.components.ConfirmDialog
import com.tagforge.app.ui.components.NfcStatusBanner
import com.tagforge.app.ui.text.TagErrorText
import com.tagforge.app.ui.text.messageText
import com.tagforge.app.ui.text.titleText
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.tag.TagError

data class CloneActions(
    val onBack: () -> Unit,
    val onCopyUserPagesChanged: (Boolean) -> Unit,
    val onStart: () -> Unit,
    val onScanTarget: () -> Unit,
    val onReset: () -> Unit,
    val onRetry: () -> Unit,
    val onSheetDismissed: () -> Unit,
    val onOverwriteDecision: (Boolean) -> Unit,
    val onOpenNfcSettings: () -> Unit,
)

/**
 * Clone in two explicit steps (TT-04): read the source, look at what will be copied, then choose the destination.
 * Presentational: state and lambdas only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneFlow(state: CloneUiState, actions: CloneActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.clone_title)) },
                navigationIcon = { BackNavigationIcon(actions.onBack) },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NfcStatusBanner(state.availability, actions.onOpenNfcSettings)
            Text(
                stringResource(if (state.step == CloneStep.SOURCE) R.string.clone_step_1_of_2 else R.string.clone_step_2_of_2),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            when (state.step) {
                CloneStep.SOURCE -> SourceStep(state, actions)
                CloneStep.TARGET -> TargetStep(state, actions)
            }
        }
    }

    state.overwrite?.let { prompt ->
        ConfirmDialog(
            title = stringResource(R.string.clone_overwrite_title),
            body = prompt.existingSummary?.let { stringResource(R.string.clone_overwrite_body, it) } ?: stringResource(R.string.clone_overwrite_body_unreadable),
            confirmLabel = stringResource(R.string.clone_overwrite_confirm),
            onConfirm = { actions.onOverwriteDecision(true) },
            onDismiss = { actions.onOverwriteDecision(false) },
            destructive = true,
        )
    }

    state.sheet?.let { sheet ->
        ToolScanSheet(
            ui = sheet.toUi(),
            onDismiss = actions.onSheetDismissed,
            onRetry = actions.onRetry,
            onExtra = actions.onScanTarget,
        )
    }
}

@Composable
private fun SourceStep(state: CloneUiState, actions: CloneActions) {
    Text(stringResource(R.string.clone_step1_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.clone_step1_body), style = MaterialTheme.typography.bodyLarge)

    val label = stringResource(R.string.clone_copy_pages)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.clone_copy_pages_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = state.copyUserPages,
            onCheckedChange = actions.onCopyUserPagesChanged,
            modifier = Modifier.semantics { role = Role.Switch },
        )
    }

    Button(onClick = actions.onStart, enabled = state.canStart, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.clone_start))
    }
}

@Composable
private fun TargetStep(state: CloneUiState, actions: CloneActions) {
    val source = state.source ?: return
    Text(stringResource(R.string.clone_step2_title), style = MaterialTheme.typography.titleLarge)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.clone_source_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.clone_source_summary, source.chip ?: stringResource(R.string.common_unknown), source.messageSize),
                style = MaterialTheme.typography.titleMedium,
            )
            if (source.summary.isNotEmpty()) Text(source.summary, style = MaterialTheme.typography.bodyMedium)
            Text(source.uid.toHex(":"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(if (source.withUserPages) R.string.clone_mode_pages else R.string.clone_mode_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.clone_not_uid_note), style = MaterialTheme.typography.bodyMedium)
    }

    Button(onClick = actions.onScanTarget, enabled = state.canStart, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.clone_scan_target))
    }
    OutlinedButton(onClick = actions.onReset, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.clone_start_over))
    }
}

@Composable
private fun CloneSheet.toUi(): ToolSheetUi = when (this) {
    is CloneSheet.Waiting -> ToolSheetUi.Waiting(
        title = stringResource(if (step == CloneStep.SOURCE) R.string.clone_sheet_source_waiting else R.string.clone_sheet_target_waiting),
        hint = stringResource(R.string.tools_sheet_waiting_hint),
    )
    is CloneSheet.Working -> ToolSheetUi.Working(
        title = stringResource(
            when {
                phase == OperationPhase.WRITING -> R.string.clone_sheet_writing
                phase == OperationPhase.VERIFYING -> R.string.tools_sheet_verifying
                step == CloneStep.SOURCE -> R.string.clone_sheet_reading
                else -> R.string.tools_sheet_connecting
            },
        ),
        hint = stringResource(R.string.tools_sheet_keep_still),
    )
    is CloneSheet.Success -> ToolSheetUi.Success(
        title = stringResource(R.string.clone_sheet_success),
        detail = stringResource(
            R.string.clone_sheet_success_detail,
            report.messageSize,
            report.target.ntag?.displayName ?: stringResource(R.string.common_unknown),
        ),
        extraLabel = stringResource(R.string.clone_sheet_again),
    )
    is CloneSheet.Failure -> {
        val text = TagErrorText.of(error)
        ToolSheetUi.Failure(
            title = text.titleText() ?: stringResource(R.string.clone_failure_title),
            message = text.messageText(),
            hint = cloneFailureHint(error)?.let { stringResource(it) },
            canRetry = canRetry,
        )
    }
}

private fun cloneFailureHint(error: TagError): Int? = when (error) {
    TagError.TagLost -> R.string.clone_hint_taglost
    TagError.ReadOnly -> R.string.tools_hint_read_only
    is TagError.CapacityExceeded -> R.string.clone_hint_capacity
    is TagError.VerifyMismatch -> R.string.tools_hint_verify
    else -> null
}
