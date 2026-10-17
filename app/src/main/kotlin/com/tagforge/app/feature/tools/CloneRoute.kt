package com.tagforge.app.feature.tools

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagforge.app.di.rememberAppContainer
import com.tagforge.app.di.tagForgeViewModel
import com.tagforge.app.ui.haptics.rememberHaptics
import com.tagforge.domain.settings.AppSettings

/** Container of the clone flow: owns the ViewModel and haptics; back steps back before it leaves the screen. */
@Composable
fun CloneRoute(onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel = tagForgeViewModel {
        CloneViewModel(
            coordinator = it.scanCoordinator,
            recordHistory = it.recordHistory,
            availability = it.nfcAvailabilityMonitor.availability,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(AppSettings())
    val haptics by rememberUpdatedState(rememberHaptics(settings.hapticsEnabled))
    val context = LocalContext.current

    LifecycleStartEffect(viewModel) {
        onStopOrDispose { viewModel.onHidden() }
    }

    // The bottom sheet handles its own back press; this one steps back from the destination to the source step.
    BackHandler(enabled = state.step == CloneStep.TARGET && state.sheet == null) { viewModel.onBack() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CloneEvent.TagDetected -> haptics.tick()
                CloneEvent.SourceCaptured, CloneEvent.Succeeded -> haptics.confirm()
                CloneEvent.Failed -> haptics.reject()
            }
        }
    }

    CloneFlow(
        state = state,
        actions = CloneActions(
            onBack = { if (!viewModel.onBack()) onBack() },
            onCopyUserPagesChanged = viewModel::onCopyUserPagesChanged,
            onStart = viewModel::onStart,
            onScanTarget = viewModel::onScanTarget,
            onReset = viewModel::onReset,
            onRetry = viewModel::onRetry,
            onSheetDismissed = viewModel::onSheetDismissed,
            onOverwriteDecision = viewModel::onOverwriteDecision,
            onOpenNfcSettings = { openNfcSettings(context) },
        ),
    )
}
