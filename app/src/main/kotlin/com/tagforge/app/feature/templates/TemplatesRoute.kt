package com.tagforge.app.feature.templates

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagforge.app.R
import com.tagforge.app.di.rememberAppContainer
import com.tagforge.app.di.tagForgeViewModel
import com.tagforge.app.infrastructure.android.PinResult
import com.tagforge.app.infrastructure.android.ShortcutPinner
import kotlinx.coroutines.launch

/** Container for the template list: owns the ViewModel and the delete-with-Undo snackbar. */
@Composable
fun TemplatesRoute(onBack: () -> Unit, onOpen: (Long) -> Unit, onShowQr: (String) -> Unit, onGoToWrite: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel = tagForgeViewModel { TemplatesViewModel(it.templateRepository, it.clock) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.templates_deleted)
    val undoLabel = stringResource(R.string.write_undo)
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

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
        onPin = { template ->
            scope.launch {
                val nonce = container.shortcutNonceStore.getOrCreate()
                val result = ShortcutPinner.pinTemplate(context, template.id, template.name, nonce)
                val message = if (result == PinResult.REQUESTED) R.string.templates_pin_requested else R.string.templates_pin_unsupported
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(resources.getString(message))
            }
        },
        onShowQr = onShowQr,
        onGoToWrite = onGoToWrite,
    )
}
