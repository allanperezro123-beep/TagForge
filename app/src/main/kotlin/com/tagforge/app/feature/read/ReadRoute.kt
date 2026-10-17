package com.tagforge.app.feature.read

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagforge.app.R
import com.tagforge.app.di.rememberAppContainer
import com.tagforge.app.di.tagForgeViewModel
import com.tagforge.app.feature.taskrunner.TaskRunnerActivity
import com.tagforge.app.ui.haptics.rememberHaptics
import com.tagforge.domain.read.ReadFullTagUseCase
import com.tagforge.domain.settings.AppSettings

/**
 * Container for the Read tab: owns the ViewModel, the lifecycle wiring (listen while visible), haptics and the
 * snackbar, and hands plain state and lambdas to [ReadScreen].
 */
@Composable
fun ReadRoute() {
    val container = rememberAppContainer()
    val viewModel = tagForgeViewModel {
        ReadViewModel(
            coordinator = it.scanCoordinator,
            operation = ReadFullTagUseCase(it.readTagUseCase, it.readMemoryUseCase),
            availability = it.nfcAvailabilityMonitor.availability,
            recordHistory = it.recordHistory,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(AppSettings())
    val haptics = rememberHaptics(settings.hapticsEnabled)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val unlockMessage = stringResource(R.string.read_unlock_unavailable)
    val currentHaptics by rememberUpdatedState(haptics)

    LifecycleStartEffect(viewModel) {
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ReadEvent.TagDetected -> currentHaptics.tick()
                ReadEvent.Succeeded -> currentHaptics.confirm()
                ReadEvent.Failed -> currentHaptics.reject()
                ReadEvent.UnlockUnavailable -> snackbarHostState.showSnackbar(unlockMessage)
                is ReadEvent.OpenTaskRunner -> context.startActivity(TaskRunnerActivity.forTagMessage(context, event.ndef.toByteArray()))
            }
        }
    }

    ReadScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onOpenNfcSettings = {
            try {
                context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
        },
        onRetry = viewModel::onRetry,
        onUnlock = viewModel::onUnlockClicked,
        onRunTask = viewModel::onRunTask,
    )
}
