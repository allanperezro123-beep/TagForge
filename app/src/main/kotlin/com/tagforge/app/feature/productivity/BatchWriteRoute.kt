package com.tagforge.app.feature.productivity

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagforge.app.BuildConfig
import com.tagforge.app.di.rememberAppContainer
import com.tagforge.app.di.tagForgeViewModel
import com.tagforge.app.ui.haptics.rememberHaptics
import com.tagforge.domain.settings.AppSettings

/**
 * Container for batch write: owns the ViewModel and haptics. The tag is only armed while the screen is visible, and
 * a write that has already started is never aborted by leaving.
 */
@Composable
fun BatchWriteRoute(onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel = tagForgeViewModel {
        BatchWriteViewModel(
            coordinator = it.scanCoordinator,
            settings = it.settingsStore,
            recordHistory = it.recordHistory,
            templates = it.templateRepository,
            appPackage = BuildConfig.APPLICATION_ID,
            availability = it.nfcAvailabilityMonitor.availability,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(AppSettings())
    val haptics by rememberUpdatedState(rememberHaptics(settings.hapticsEnabled))

    LifecycleStartEffect(viewModel) {
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                BatchEvent.TagDetected -> haptics.tick()
                BatchEvent.Succeeded -> haptics.confirm()
                BatchEvent.Failed -> haptics.reject()
            }
        }
    }
    // Leaving a running batch with back finishes it first, so the summary is not lost silently.
    BackHandler(enabled = state.step == BatchStep.RUNNING) { viewModel.onStop() }

    val actions = BatchActions(
        setup = BatchSetupActions(
            onTemplateSelected = viewModel::onTemplateSelected,
            onStartChanged = viewModel::onStartChanged,
            onStepChanged = viewModel::onStepChanged,
            onPadChanged = viewModel::onPadChanged,
            onTotalChanged = viewModel::onTotalChanged,
            onAarChanged = viewModel::onAarChanged,
            onTargetSelected = viewModel::onTargetSelected,
            onStart = viewModel::onStart,
        ),
        onSkip = viewModel::onSkip,
        onStop = viewModel::onStop,
        onNewBatch = viewModel::onNewBatch,
        onPromptDecision = viewModel::onPromptDecision,
        onBack = onBack,
    )
    BatchWriteScreen(state = state, actions = actions)
}
