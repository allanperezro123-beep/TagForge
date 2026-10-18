package com.tagforge.app.feature.productivity

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
import com.tagforge.app.ui.components.BackNavigationIcon
import com.tagforge.app.ui.components.ConfirmDialog

/** Everything the batch screen can ask for, so the composables stay free of the ViewModel. */
data class BatchActions(
    val setup: BatchSetupActions,
    val onSkip: () -> Unit,
    val onStop: () -> Unit,
    val onNewBatch: () -> Unit,
    val onPromptDecision: (Boolean) -> Unit,
    val onBack: () -> Unit,
)

/** Presentational batch write screen (PR-01): setup, running and summary share one scaffold. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchWriteScreen(state: BatchUiState, actions: BatchActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.batch_title)) },
                navigationIcon = { BackNavigationIcon(actions.onBack) },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            when (state.step) {
                BatchStep.SETUP -> BatchSetupContent(state, actions.setup)
                BatchStep.RUNNING -> BatchRunningContent(state, actions.onSkip, actions.onStop)
                BatchStep.DONE -> BatchDoneContent(state, actions.onNewBatch)
            }
        }
    }
    state.prompt?.let { prompt -> PromptDialog(prompt, actions.onPromptDecision) }
}

@Composable
private fun PromptDialog(prompt: BatchPrompt, onDecision: (Boolean) -> Unit) {
    val body = when (prompt) {
        BatchPrompt.SameTag -> stringResource(R.string.batch_prompt_same_tag)
        is BatchPrompt.Overwrite -> prompt.existingSummary
            ?.let { stringResource(R.string.batch_prompt_overwrite_with, it) }
            ?: stringResource(R.string.batch_prompt_overwrite)
    }
    ConfirmDialog(
        title = stringResource(R.string.batch_prompt_title),
        body = body,
        confirmLabel = stringResource(R.string.batch_prompt_confirm),
        onConfirm = { onDecision(true) },
        onDismiss = { onDecision(false) },
        destructive = true,
    )
}
