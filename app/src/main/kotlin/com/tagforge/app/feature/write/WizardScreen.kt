package com.tagforge.app.feature.write

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.app.ui.components.ConfirmDialog
import com.tagforge.app.ui.components.NfcStatusBanner
import com.tagforge.app.ui.components.BackNavigationIcon

private const val SLIDE_FRACTION = 4

/**
 * Presentational Write screen: renders [WriteUiState] and reports intent through [WriteActions]. It has no ViewModel,
 * repository or NfcAdapter, so it renders with fixed state (AS-05).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScreen(
    state: WriteUiState,
    actions: WriteActions,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.write_title)) },
                navigationIcon = { BackNavigationIcon(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.availability != NfcAvailability.ENABLED) {
                NfcStatusBanner(state.availability, actions.onOpenNfcSettings, Modifier.padding(16.dp))
            }
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    val enter = slideInHorizontally { if (forward) it / SLIDE_FRACTION else -it / SLIDE_FRACTION } + fadeIn()
                    val exit = slideOutHorizontally { if (forward) -it / SLIDE_FRACTION else it / SLIDE_FRACTION } + fadeOut()
                    enter togetherWith exit
                },
                label = "wizard-step",
            ) { step ->
                when (step) {
                    WriteStep.TYPE -> RecordTypePicker(actions.onTypeSelected, hasRecords = state.records.isNotEmpty())
                    WriteStep.FORM -> state.editing?.let { editing ->
                        FormStep(state, editing, actions.onDraftChanged, actions.onFieldTouched, actions.onContinue)
                    }
                    WriteStep.REVIEW -> ReviewStep(state, actions)
                }
            }
        }
    }

    state.sheet?.let { sheet ->
        WriteScanSheet(
            sheet = sheet,
            onDismiss = actions.onSheetDismissed,
            onRetry = actions.onRetry,
            onWriteAnother = actions.onWriteAnother,
            onSaveTemplate = actions.onSaveTemplate,
        )
    }

    state.overwrite?.let { prompt ->
        ConfirmDialog(
            title = stringResource(R.string.write_overwrite_title),
            body = prompt.existingSummary?.let { stringResource(R.string.write_overwrite_body, it) }
                ?: stringResource(R.string.write_overwrite_unreadable),
            confirmLabel = stringResource(R.string.write_overwrite_confirm),
            destructive = true,
            onConfirm = { actions.onOverwriteDecision(true) },
            onDismiss = { actions.onOverwriteDecision(false) },
        )
    }
}
