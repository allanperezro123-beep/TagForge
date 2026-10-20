package com.tagforge.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.domain.settings.TaskExecutionPolicy

/** Everything the "Confirmations" section can do, as lambdas so the section renders without a ViewModel. */
data class ConfirmationActions(
    val onOverwriteChanged: (Boolean) -> Unit,
    val onEraseFormatChanged: (Boolean) -> Unit,
    val onPolicySelected: (TaskExecutionPolicy) -> Unit,
    val onRiskAccepted: () -> Unit,
    val onRiskDismissed: () -> Unit,
    val onForgetTrusted: () -> Unit,
)

/** Which confirmations can be turned off, and the one that never can: irreversible tag security operations. */
@Composable
fun ConfirmationsSection(state: SettingsUiState, actions: ConfirmationActions) {
    val settings = state.settings
    Text(
        stringResource(R.string.settings_confirm_irreversible),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    ConfirmSwitch(R.string.settings_confirm_overwrite, R.string.settings_confirm_overwrite_desc, settings.confirmOverwrite, actions.onOverwriteChanged)
    ConfirmSwitch(R.string.settings_confirm_erase, R.string.settings_confirm_erase_desc, settings.confirmEraseFormat, actions.onEraseFormatChanged)

    Text(
        stringResource(R.string.settings_tasks_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
    )
    Column(Modifier.selectableGroup()) {
        PolicyOption(R.string.settings_tasks_ask, R.string.settings_tasks_ask_desc, TaskExecutionPolicy.ASK_ALWAYS, settings.taskExecution, actions.onPolicySelected)
        PolicyOption(R.string.settings_tasks_own, R.string.settings_tasks_own_desc, TaskExecutionPolicy.OWN_TASKS, settings.taskExecution, actions.onPolicySelected)
        PolicyOption(R.string.settings_tasks_never, R.string.settings_tasks_never_desc, TaskExecutionPolicy.NEVER_ASK, settings.taskExecution, actions.onPolicySelected)
    }
    when (settings.taskExecution) {
        TaskExecutionPolicy.NEVER_ASK -> RiskHint(actions)
        TaskExecutionPolicy.OWN_TASKS -> TrustedHint(settings.trustedTaskFingerprints.size, actions.onForgetTrusted)
        TaskExecutionPolicy.ASK_ALWAYS -> Unit
    }
    if (state.riskDialogVisible) RiskDialog(actions)
}

@Composable
private fun ConfirmSwitch(title: Int, description: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(description)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun PolicyOption(title: Int, description: Int, option: TaskExecutionPolicy, selected: TaskExecutionPolicy, onSelect: (TaskExecutionPolicy) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .selectable(selected = option == selected, onClick = { onSelect(option) }, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = option == selected, onClick = null)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Visible for as long as "never ask" is on, with the one-tap way back. */
@Composable
private fun RiskHint(actions: ConfirmationActions) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.settings_tasks_never_active), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { actions.onPolicySelected(TaskExecutionPolicy.ASK_ALWAYS) }) { Text(stringResource(R.string.settings_tasks_back_to_ask)) }
    }
}

@Composable
private fun TrustedHint(count: Int, onForget: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.settings_tasks_trusted_count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (count > 0) TextButton(onClick = onForget) { Text(stringResource(R.string.settings_tasks_forget)) }
    }
}

@Composable
private fun RiskDialog(actions: ConfirmationActions) {
    AlertDialog(
        onDismissRequest = actions.onRiskDismissed,
        title = { Text(stringResource(R.string.settings_tasks_risk_title)) },
        text = { Text(stringResource(R.string.settings_tasks_risk_body)) },
        confirmButton = { TextButton(onClick = actions.onRiskAccepted) { Text(stringResource(R.string.settings_tasks_risk_accept)) } },
        dismissButton = { TextButton(onClick = actions.onRiskDismissed) { Text(stringResource(R.string.common_cancel)) } },
    )
}
