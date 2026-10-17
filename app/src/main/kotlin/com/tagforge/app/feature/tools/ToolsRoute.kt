package com.tagforge.app.feature.tools

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
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

/** Container of the Tools tab: owns the ViewModel, haptics and the NFC settings intent. */
@Composable
fun ToolsRoute(onOpenDump: () -> Unit, onOpenClone: () -> Unit, onOpenTech: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel = tagForgeViewModel {
        ToolsViewModel(
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

    BackHandler(enabled = state.confirm != null) { viewModel.onBack() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ToolsEvent.TagDetected -> haptics.tick()
                ToolsEvent.Succeeded -> haptics.confirm()
                ToolsEvent.Failed -> haptics.reject()
            }
        }
    }

    ToolsScreen(
        state = state,
        actions = ToolsActions(
            onErase = { viewModel.onRequest(DestructiveTool.ERASE) },
            onFormat = { viewModel.onRequest(DestructiveTool.FORMAT) },
            onDump = onOpenDump,
            onClone = onOpenClone,
            onTech = onOpenTech,
            onConfirm = viewModel::onConfirm,
            onCancelConfirm = viewModel::onCancelConfirm,
            onSheetDismissed = viewModel::onSheetDismissed,
            onRetry = viewModel::onRetry,
            onOpenNfcSettings = { openNfcSettings(context) },
        ),
    )
}

internal fun openNfcSettings(context: android.content.Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }
}
