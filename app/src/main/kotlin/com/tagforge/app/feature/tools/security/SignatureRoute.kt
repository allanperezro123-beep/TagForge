package com.tagforge.app.feature.tools.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagforge.app.di.rememberAppContainer
import com.tagforge.app.di.tagForgeViewModel
import com.tagforge.app.feature.tools.ToolReadEvent
import com.tagforge.app.feature.tools.openNfcSettings
import com.tagforge.app.ui.haptics.rememberHaptics
import com.tagforge.domain.settings.AppSettings

/** Container of the signature screen: arms READ_SIG while visible. */
@Composable
fun SignatureRoute(onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel = tagForgeViewModel { SignatureViewModel(it.scanCoordinator, it.nfcAvailabilityMonitor.availability) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(AppSettings())
    val haptics by rememberUpdatedState(rememberHaptics(settings.hapticsEnabled))
    val context = LocalContext.current

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

    SignatureScreen(
        state = state,
        onBack = onBack,
        onRescan = viewModel::onRescan,
        onCopy = { text ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("signature", text))
        },
        onOpenNfcSettings = { openNfcSettings(context) },
    )
}
