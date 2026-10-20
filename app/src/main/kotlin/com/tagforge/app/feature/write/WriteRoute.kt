package com.tagforge.app.feature.write

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.tagforge.domain.exchange.QrPayloadMapper
import com.tagforge.domain.location.SharedText
import com.tagforge.domain.location.SharedTextResult
import com.tagforge.domain.ndef.spec.LanguageCodes
import com.tagforge.domain.settings.AppSettings
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Container for the Write tab: owns the ViewModel, haptics, snackbars and system intents, and hands plain state and
 * lambdas to [WizardScreen]. [templateId] opens the wizard prefilled from a saved template.
 */
@Composable
fun WriteRoute(
    templateId: Long? = null,
    qrText: String? = null,
    armOnOpen: Boolean = false,
    sharedText: String? = null,
    onShowQr: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val container = rememberAppContainer()
    val viewModel = tagForgeViewModel {
        WriteViewModel(
            coordinator = it.scanCoordinator,
            settings = it.settingsStore,
            recordHistory = it.recordHistory,
            templates = it.templateRepository,
            clock = it.clock,
            appPackage = BuildConfig.APPLICATION_ID,
            availability = it.nfcAvailabilityMonitor.availability,
            initialTemplateId = templateId,
            initialRecords = qrText?.let { text -> listOf(QrPayloadMapper.fromQr(text)) }
                ?: (SharedText.parse(sharedText) as? SharedTextResult.Record)?.let { listOf(it.spec) }.orEmpty(),
            armOnOpen = armOnOpen,
            defaultLanguage = LanguageCodes.fromDeviceTag(Locale.getDefault().toLanguageTag()),
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(AppSettings())
    val haptics by rememberUpdatedState(rememberHaptics(settings.hapticsEnabled))
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    var showTemplateDialog by rememberSaveable { mutableStateOf(false) }

    val removedMessage = stringResource(R.string.write_record_removed)
    val undoLabel = stringResource(R.string.write_undo)

    LifecycleStartEffect(viewModel) {
        onStopOrDispose { viewModel.onHidden() }
    }

    // Back steps through the wizard first (review -> form -> type); with no earlier step it falls through to the host.
    BackHandler(enabled = state.sheet == null && state.canStepBack) {
        viewModel.onBack()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WriteEvent.TagDetected -> haptics.tick()
                WriteEvent.Succeeded -> haptics.confirm()
                WriteEvent.Failed -> haptics.reject()
                is WriteEvent.TemplateSaved -> scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(resources.getString(R.string.write_template_saved, event.name))
                }
                WriteEvent.RecordRemoved -> scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(removedMessage, undoLabel, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndoRemove()
                }
            }
        }
    }

    val actions = WriteActions(
        onTypeSelected = viewModel::onTypeSelected,
        onDraftChanged = viewModel::onDraftChanged,
        onFieldTouched = viewModel::onFieldTouched,
        onContinue = viewModel::onContinue,
        onAddRecord = viewModel::onAddRecord,
        onEditRecord = viewModel::onEditRecord,
        onRemoveRecord = viewModel::onRemoveRecord,
        onMoveRecord = viewModel::onMoveRecord,
        onIncludeAarChanged = viewModel::onIncludeAarChanged,
        onTargetSelected = viewModel::onTargetSelected,
        onWrite = viewModel::onWrite,
        onSaveTemplate = { showTemplateDialog = true },
        onShowQr = onShowQr,
        onOverwriteDecision = viewModel::onOverwriteDecision,
        onRetry = viewModel::onRetry,
        onWriteAnother = viewModel::onWriteAnother,
        onSheetDismissed = viewModel::onSheetDismissed,
        onUndoRemove = viewModel::onUndoRemove,
        onOpenNfcSettings = {
            try {
                context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
        },
    )

    WizardScreen(
        state = state,
        actions = actions,
        snackbarHostState = snackbarHostState,
        onBack = { if (!viewModel.onBack()) onBack() },
    )

    if (showTemplateDialog) {
        SaveTemplateDialog(
            onSave = { name ->
                showTemplateDialog = false
                viewModel.onSaveTemplate(name)
            },
            onDismiss = { showTemplateDialog = false },
        )
    }
}

@Composable
private fun SaveTemplateDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val suggestion = stringResource(R.string.write_template_default_name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.write_save_template)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.write_template_name)) },
                placeholder = { Text(suggestion) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.ifBlank { suggestion }) }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
