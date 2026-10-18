package com.tagforge.app.feature.tasks

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagforge.app.BuildConfig
import com.tagforge.app.R
import com.tagforge.app.di.rememberAppContainer
import com.tagforge.app.di.tagForgeViewModel
import com.tagforge.app.ui.haptics.rememberHaptics
import com.tagforge.domain.settings.AppSettings
import kotlinx.coroutines.launch

/** Container for the task builder: owns the ViewModel, haptics, snackbars and the system intent for NFC settings. */
@Composable
fun TaskBuilderRoute(taskId: Long?, onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel = tagForgeViewModel {
        TaskBuilderViewModel(
            coordinator = it.scanCoordinator,
            settings = it.settingsStore,
            recordHistory = it.recordHistory,
            tasks = it.taskRepository,
            clock = it.clock,
            appPackage = BuildConfig.APPLICATION_ID,
            availability = it.nfcAvailabilityMonitor.availability,
            taskId = taskId,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(AppSettings())
    val haptics by rememberUpdatedState(rememberHaptics(settings.hapticsEnabled))
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val removedMessage = stringResource(R.string.builder_action_removed)
    val undoLabel = stringResource(R.string.write_undo)

    LifecycleStartEffect(viewModel) {
        onStopOrDispose { viewModel.onHidden() }
    }

    BackHandler(enabled = state.editing != null || state.picking) { viewModel.onBack() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                TaskBuilderEvent.TagDetected -> haptics.tick()
                TaskBuilderEvent.WriteSucceeded -> haptics.confirm()
                TaskBuilderEvent.WriteFailed -> haptics.reject()
                is TaskBuilderEvent.Saved -> scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(resources.getString(R.string.builder_saved, event.name))
                }
                TaskBuilderEvent.ActionRemoved -> scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(removedMessage, undoLabel, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndoRemove()
                }
            }
        }
    }

    val actions = TaskBuilderActions(
        onNameChanged = viewModel::onNameChanged,
        onAddAction = viewModel::onAddAction,
        onDismissPicker = viewModel::onDismissPicker,
        onKindSelected = viewModel::onKindSelected,
        onActionChanged = viewModel::onActionChanged,
        onConfirmAction = viewModel::onConfirmAction,
        onCancelEditing = viewModel::onCancelEditing,
        onEditAction = viewModel::onEditAction,
        onRemoveAction = viewModel::onRemoveAction,
        onMoveAction = viewModel::onMoveAction,
        onTargetSelected = viewModel::onTargetSelected,
        onSave = viewModel::onSave,
        onWrite = viewModel::onWrite,
        onRetry = viewModel::onRetry,
        onOverwriteDecision = viewModel::onOverwriteDecision,
        onSheetDismissed = viewModel::onSheetDismissed,
        onOpenNfcSettings = {
            try {
                context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
        },
    )

    TaskBuilderScreen(state = state, actions = actions, snackbarHostState = snackbarHostState, onBack = onBack)
}
