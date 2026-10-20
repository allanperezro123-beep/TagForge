package com.tagforge.app.feature.taskrunner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.text.TaskText
import com.tagforge.domain.settings.TaskExecutionPolicy
import com.tagforge.domain.task.PlanItem
import com.tagforge.domain.task.TaskPlan

/**
 * The sheet every task goes through (TK-06). It lists EVERY action in plain language with its full parameters,
 * marks the risky ones, warns about unencrypted requests, and keeps focus on Cancel. Back, a tap outside and Cancel
 * are all "no". While a task is running the sheet cannot be swiped away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskConfirmationSheet(
    state: TaskRunnerUiState,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
    onUntrust: () -> Unit = {},
    onAskAlways: () -> Unit = {},
) {
    if (state == TaskRunnerUiState.Idle) return
    val running = state is TaskRunnerUiState.Running
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !running })
    ModalBottomSheet(
        onDismissRequest = { if (state is TaskRunnerUiState.Confirming) onCancel() else onClose() },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                TaskRunnerUiState.Idle -> Unit
                is TaskRunnerUiState.Error -> ErrorContent(state, onClose)
                is TaskRunnerUiState.Confirming -> ConfirmContent(state, onRun, onCancel, onFix)
                is TaskRunnerUiState.Running -> {
                    Header(state.plan)
                    if (state.auto != null) AutoRunNote()
                    Rows(state.rows.map { it.item }, state.rows.map { it.status }, onFix)
                }
                is TaskRunnerUiState.Finished -> FinishedContent(state, onClose, onFix, onUntrust, onAskAlways)
            }
        }
    }
}

@Composable
private fun Header(plan: TaskPlan) {
    Text(
        plan.name ?: stringResource(R.string.task_runner_title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun Rows(items: List<PlanItem>, statuses: List<RowStatus?>, onFix: () -> Unit) {
    items.forEachIndexed { index, item ->
        if (index > 0) HorizontalDivider()
        TaskActionRow(index, items.size, item, statuses.getOrNull(index), onFix)
    }
}

@Composable
private fun ConfirmContent(state: TaskRunnerUiState.Confirming, onRun: () -> Unit, onCancel: () -> Unit, onFix: () -> Unit) {
    Header(state.plan)
    Text(
        stringResource(R.string.task_runner_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.plan.items.isEmpty()) {
        Text(stringResource(R.string.task_runner_nothing), style = MaterialTheme.typography.bodyLarge)
    } else {
        Rows(state.plan.items, List(state.plan.items.size) { null }, onFix)
    }
    if (state.plan.requiresSecondTap) InsecureWarning(armed = state.armed)

    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Default focus is Cancel (TK-06). The sheet may not be attached yet on the very first frame.
        runCatching { cancelFocus.requestFocus() }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).focusRequester(cancelFocus)) {
            Text(stringResource(R.string.common_cancel))
        }
        Button(
            onClick = onRun,
            enabled = state.canRun,
            colors = if (state.armed) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors(),
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(if (state.armed) R.string.task_runner_run_unencrypted else R.string.task_runner_run))
        }
    }
}

@Composable
private fun InsecureWarning(armed: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().semantics { if (armed) liveRegion = LiveRegionMode.Assertive },
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.task_runner_http_warning), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FinishedContent(
    state: TaskRunnerUiState.Finished,
    onClose: () -> Unit,
    onFix: () -> Unit,
    onUntrust: () -> Unit,
    onAskAlways: () -> Unit,
) {
    Header(state.plan)
    if (state.auto != null) AutoRunNote()
    Text(
        stringResource(if (state.needsAttention) R.string.task_runner_finished_attention else R.string.task_runner_finished_ok),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Rows(state.plan.items, state.results.map { RowStatus.Finished(it.outcome) }, onFix)
    state.auto?.let { AutoRunActions(it, onUntrust, onAskAlways) }
    Button(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.common_close)) }
}

@Composable
private fun AutoRunNote() {
    Text(
        stringResource(R.string.task_runner_ran_automatically),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The way back from a task that ran by itself: stop trusting that task, or go back to always asking. */
@Composable
private fun AutoRunActions(auto: AutoRun, onUntrust: () -> Unit, onAskAlways: () -> Unit) {
    val notice = when (auto.notice) {
        AutoRunNotice.UNTRUSTED -> R.string.task_runner_untrusted
        AutoRunNotice.WILL_ASK -> R.string.task_runner_ask_again_done
        null -> null
    }
    if (notice != null) {
        Text(stringResource(notice), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        return
    }
    if (auto.fingerprint != null) {
        OutlinedButton(onClick = onUntrust, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.task_runner_untrust)) }
    }
    if (auto.policy == TaskExecutionPolicy.NEVER_ASK) {
        OutlinedButton(onClick = onAskAlways, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.task_runner_ask_again)) }
    }
}

@Composable
private fun ErrorContent(state: TaskRunnerUiState.Error, onClose: () -> Unit) {
    val resources = LocalResources.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
        Text(
            stringResource(TaskText.errorTitle(state.error)),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Text(TaskText.errorBody(resources, state.error), style = MaterialTheme.typography.bodyLarge)
    Button(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.common_close)) }
}
