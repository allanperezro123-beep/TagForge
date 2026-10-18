package com.tagforge.app.feature.history

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagforge.app.R
import com.tagforge.app.di.tagForgeViewModel

/** Container for the history list: owns the ViewModel and the delete-with-Undo snackbar. */
@Composable
fun HistoryRoute(onBack: () -> Unit, onOpen: (Long) -> Unit, onScan: () -> Unit) {
    val viewModel = tagForgeViewModel { HistoryViewModel(it.historyRepository, it.clock) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.history_deleted)
    val undoLabel = stringResource(R.string.write_undo)

    // One snackbar per deletion; its Undo action restores the entry.
    LaunchedEffect(state.lastDeleted?.id) {
        if (state.lastDeleted != null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.onUndoDelete()
        }
    }

    HistoryScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpen = onOpen,
        onDelete = viewModel::onDelete,
        onClearRequested = viewModel::onClearRequested,
        onClearConfirmed = viewModel::onClearConfirmed,
        onClearDismissed = viewModel::onClearDismissed,
        onScan = onScan,
        onSearchChanged = viewModel::onSearchChanged,
        onToggleKind = viewModel::onToggleKind,
        onOutcomeChanged = viewModel::onOutcomeChanged,
        onRangeChanged = viewModel::onRangeChanged,
        onClearFilters = viewModel::onClearFilters,
    )
}
