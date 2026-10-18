package com.tagforge.app.feature.tools.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
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
                onCopy = { copySensitive(context, it) },
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

/** Copies [text] and tells Android it is sensitive so keyboards and the clipboard preview do not show it. */
private fun copySensitive(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("tag password", text)
    clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
    clipboard.setPrimaryClip(clip)
}

private fun shareToSelf(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
