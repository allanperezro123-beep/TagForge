package com.tagforge.app.feature.tools.security

import android.content.Context
import android.content.Intent
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
import com.tagforge.app.feature.tools.openNfcSettings
import com.tagforge.app.infrastructure.android.SensitiveClipboard
import com.tagforge.app.ui.components.SecureWindow
import com.tagforge.app.ui.haptics.rememberHaptics
import com.tagforge.domain.settings.AppSettings

/** Container of the security screens: owns the ViewModel, haptics, back handling and the copy/share-to-self tools. */
@Composable
fun SecurityRoute(
    initialAction: SecurityAction?,
    onBack: () -> Unit,
    onOpenSignature: () -> Unit,
    onOpenConsole: () -> Unit,
) {
    val container = rememberAppContainer()
    val viewModel = tagForgeViewModel {
        SecurityViewModel(
            coordinator = it.scanCoordinator,
            recordHistory = it.recordHistory,
            availability = it.nfcAvailabilityMonitor.availability,
            settings = it.settingsStore.settings,
        )
    }
    SecureWindow()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(AppSettings())
    val haptics by rememberUpdatedState(rememberHaptics(settings.hapticsEnabled))
    val context = LocalContext.current

    LaunchedEffect(initialAction) {
        if (initialAction != null && viewModel.uiState.value.action == null) viewModel.onChoose(initialAction)
    }
    LifecycleStartEffect(viewModel) {
        onStopOrDispose { viewModel.onHidden() }
    }
    BackHandler { if (!viewModel.onBack()) onBack() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SecurityEvent.TagDetected -> haptics.tick()
                SecurityEvent.Succeeded -> haptics.confirm()
                SecurityEvent.Failed -> haptics.reject()
            }
        }
    }

    SecurityScreen(
        state = state,
        actions = SecurityActions(
            onBack = { if (!viewModel.onBack()) onBack() },
            onChoose = viewModel::onChoose,
            form = SecurityFormActions(
                onChange = viewModel::onFormChange,
                onGenerate = viewModel::onGeneratePassword,
                onLimitUnderstood = viewModel::onLimitUnderstood,
                onProbeLimit = viewModel::onProbeLimit,
                onNext = viewModel::onNext,
                onCopy = { SensitiveClipboard.copy(context, "tag password", it) },
                onShare = { shareToSelf(context, it) },
            ),
            onTypedChange = viewModel::onTypedChange,
            onUnderstoodChange = viewModel::onUnderstoodChange,
            onConfirm = viewModel::onConfirm,
            onCancelDryRun = { viewModel.onBack() },
            onSheetDismissed = viewModel::onSheetDismissed,
            onRetry = viewModel::onRetry,
            onOpenSignature = onOpenSignature,
            onOpenConsole = onOpenConsole,
            onOpenNfcSettings = { openNfcSettings(context) },
        ),
    )
}

private fun shareToSelf(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

