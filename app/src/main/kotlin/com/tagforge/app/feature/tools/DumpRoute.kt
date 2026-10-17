package com.tagforge.app.feature.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagforge.app.R
import com.tagforge.app.di.rememberAppContainer
import com.tagforge.app.di.tagForgeViewModel
import com.tagforge.app.infrastructure.android.ShareExporter
import com.tagforge.app.ui.haptics.rememberHaptics
import com.tagforge.domain.settings.AppSettings
import com.tagforge.domain.tools.DumpFormats

/** Container of the memory dump: arms the dump while visible, owns haptics, clipboard and the share sheet. */
@Composable
fun DumpRoute(onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel = tagForgeViewModel { DumpViewModel(it.scanCoordinator, it.nfcAvailabilityMonitor.availability) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(AppSettings())
    val haptics by rememberUpdatedState(rememberHaptics(settings.hapticsEnabled))
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val exporter = remember(context) { ShareExporter(context) }
    val copiedMessage = resources.getString(R.string.dump_copied)

    LifecycleStartEffect(viewModel) {
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ToolReadEvent.TagDetected -> haptics.tick()
                ToolReadEvent.Succeeded -> haptics.confirm()
                ToolReadEvent.Failed -> haptics.reject()
            }
        }
    }

    DumpScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        actions = DumpActions(
            onBack = onBack,
            onRescan = viewModel::onRescan,
            onLayoutChanged = viewModel::onLayoutChanged,
            onPageSelected = viewModel::onPageSelected,
            onCopy = {
                state.report?.let { report ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(resources.getString(R.string.dump_title), DumpFormats.toText(report)))
                    // Android 13+ shows its own confirmation; older versions need ours.
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                        viewModel.onCopied()
                    }
                }
            },
            onShareText = { state.report?.let(exporter::shareText) },
            onShareBinary = { state.report?.let(exporter::shareBinary) },
            onOpenNfcSettings = { openNfcSettings(context) },
        ),
    )

    LaunchedEffect(state.copyNotice) {
        if (state.copyNotice) {
            snackbarHostState.showSnackbar(copiedMessage)
            viewModel.onCopyNoticeShown()
        }
    }
}
