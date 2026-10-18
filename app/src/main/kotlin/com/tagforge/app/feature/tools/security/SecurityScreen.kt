package com.tagforge.app.feature.tools.security

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.feature.tools.ToolScanSheet
import com.tagforge.app.ui.components.BackNavigationIcon
import com.tagforge.app.ui.components.NfcStatusBanner
import com.tagforge.app.ui.components.TypedConfirmationDialog

/** Every control of the security screens as a lambda, so the screen is stateless (AS-05). */
data class SecurityActions(
    val onBack: () -> Unit,
    val onChoose: (SecurityAction) -> Unit,
    val form: SecurityFormActions,
    val onTypedChange: (String) -> Unit,
    val onUnderstoodChange: (Boolean) -> Unit,
    val onConfirm: () -> Unit,
    val onCancelDryRun: () -> Unit,
    val onSheetDismissed: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenSignature: () -> Unit,
    val onOpenConsole: () -> Unit,
    val onOpenNfcSettings: () -> Unit,
)

/** Password protection and permanent locks (NS-02 to NS-06): hub, form, the dry-run dialog and the scan sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(state: SecurityUiState, actions: SecurityActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.security_title)) },
                navigationIcon = { BackNavigationIcon(actions.onBack) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            NfcStatusBanner(state.availability, actions.onOpenNfcSettings, Modifier.padding(16.dp))
            when (state.step) {
                SecurityStep.CHOOSE -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    SecurityChooseList(state.advanced, state.canStart, actions.onChoose, actions.onOpenSignature, actions.onOpenConsole)
                }
                SecurityStep.FORM, SecurityStep.DRY_RUN -> SecurityFormContent(state, actions.form)
            }
        }
    }

    val plan = state.plan
    if (state.step == SecurityStep.DRY_RUN && plan != null && state.sheet == null) {
        TypedConfirmationDialog(
            plan = plan,
            typed = state.typed,
            understood = state.understood,
            needsUnderstood = state.needsUnderstood,
            canConfirm = state.canConfirm,
            onTypedChange = actions.onTypedChange,
            onUnderstoodChange = actions.onUnderstoodChange,
            onConfirm = actions.onConfirm,
            onDismiss = actions.onCancelDryRun,
        )
    }

    state.sheet?.let { sheet ->
        ToolScanSheet(ui = sheet.toUi(), onDismiss = actions.onSheetDismissed, onRetry = actions.onRetry)
    }
}
