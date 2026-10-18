package com.tagforge.app.feature.tools.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagforge.app.di.rememberAppContainer
import com.tagforge.app.di.tagForgeViewModel
import com.tagforge.app.feature.tools.openNfcSettings
import com.tagforge.app.ui.haptics.rememberHaptics
import com.tagforge.domain.settings.AppSettings

/** Container of the raw console. The nav host only offers this route when Advanced mode is on. */
@Composable
fun RawConsoleRoute(onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel = tagForgeViewModel {
        RawConsoleViewModel(
            coordinator = it.scanCoordinator,
            availability = it.nfcAvailabilityMonitor.availability,
            settings = it.settingsStore.settings,
            acknowledged = it.rawConsoleAcknowledged,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(AppSettings())
    val haptics by rememberUpdatedState(rememberHaptics(settings.hapticsEnabled))
    val context = LocalContext.current

    LifecycleStartEffect(viewModel) {
        onStopOrDispose { viewModel.onHidden() }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ConsoleEvent.TagDetected -> haptics.tick()
                ConsoleEvent.Succeeded -> haptics.confirm()
                ConsoleEvent.Failed -> haptics.reject()
            }
        }
    }

    RawConsoleScreen(
        state = state,
        actions = RawConsoleActions(
            onBack = onBack,
            onHexChange = viewModel::onHexChange,
            onSend = viewModel::onSend,
            onAcknowledge = viewModel::onAcknowledge,
            onLightConfirm = viewModel::onLightConfirm,
            onTypedChange = viewModel::onTypedChange,
            onTypedConfirm = viewModel::onTypedConfirm,
            onCancelPending = viewModel::onCancelPending,
            onSheetDismissed = viewModel::onSheetDismissed,
            onRetry = { viewModel.onSend() },
            onOpenNfcSettings = { openNfcSettings(context) },
        ),
    )
}
