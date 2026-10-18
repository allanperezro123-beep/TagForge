package com.tagforge.app.feature.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.feature.tasks.forms.ActionForm
import com.tagforge.app.feature.write.WriteScanSheet
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.app.ui.components.BackNavigationIcon
import com.tagforge.app.ui.components.ConfirmDialog
import com.tagforge.app.ui.components.NfcStatusBanner

/**
 * Presentational task builder: renders [TaskBuilderUiState] and reports intent through [TaskBuilderActions]. It has no
 * ViewModel, repository or NfcAdapter, so it renders with fixed state (AS-05). While an action's form is open the
 * form replaces the list, like the Write wizard's form step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskBuilderScreen(
    state: TaskBuilderUiState,
    actions: TaskBuilderActions,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = if (state.taskId == null) R.string.builder_title_new else R.string.builder_title_edit
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = { BackNavigationIcon(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.availability != NfcAvailability.ENABLED) {
                NfcStatusBanner(state.availability, actions.onOpenNfcSettings, Modifier.padding(16.dp))
            }
            val editing = state.editing
            if (editing != null) {
                ActionForm(editing, actions.onActionChanged, actions.onConfirmAction, actions.onCancelEditing)
            } else {
                TaskBuilderContent(state, actions)
            }
        }
    }

    if (state.picking) ActionPickerSheet(onSelect = actions.onKindSelected, onDismiss = actions.onDismissPicker)

    state.sheet?.let { sheet ->
        WriteScanSheet(
            sheet = sheet,
            onDismiss = actions.onSheetDismissed,
            onRetry = actions.onRetry,
            onWriteAnother = null,
            onSaveTemplate = null,
        )
    }

    state.overwrite?.let { prompt ->
        ConfirmDialog(
            title = stringResource(R.string.write_overwrite_title),
            body = prompt.existingSummary?.let { stringResource(R.string.write_overwrite_body, it) }
                ?: stringResource(R.string.write_overwrite_unreadable),
            confirmLabel = stringResource(R.string.write_overwrite_confirm),
            destructive = true,
            onConfirm = { actions.onOverwriteDecision(true) },
            onDismiss = { actions.onOverwriteDecision(false) },
        )
    }
}
