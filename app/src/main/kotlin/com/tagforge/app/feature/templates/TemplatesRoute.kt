package com.tagforge.app.feature.templates

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

/** Container for the template list: owns the ViewModel and the delete-with-Undo snackbar. */
@Composable
fun TemplatesRoute(onBack: () -> Unit, onOpen: (Long) -> Unit, onGoToWrite: () -> Unit) {
    val viewModel = tagForgeViewModel { TemplatesViewModel(it.templateRepository, it.clock) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.templates_deleted)
    val undoLabel = stringResource(R.string.write_undo)

    LaunchedEffect(state.lastDeleted?.id) {
        if (state.lastDeleted != null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.onUndoDelete()
        }
    }

    TemplatesScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpen = onOpen,
        onRename = viewModel::onRename,
        onDelete = viewModel::onDelete,
        onGoToWrite = onGoToWrite,
    )
}
