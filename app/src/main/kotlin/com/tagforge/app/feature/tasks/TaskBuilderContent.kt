package com.tagforge.app.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.feature.taskrunner.RiskBadge
import com.tagforge.app.feature.write.WriteTarget
import com.tagforge.app.feature.write.targetName
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.app.ui.components.SizeBudgetMeter
import com.tagforge.app.ui.text.TaskText
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.task.TaskBlock
import com.tagforge.domain.task.risk

/** The list side of the builder: name, ordered actions, tag size meter, and the Save / Write buttons. */
@Composable
fun TaskBuilderContent(state: TaskBuilderUiState, actions: TaskBuilderActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = actions.onNameChanged,
            label = { Text(stringResource(R.string.builder_name)) },
            placeholder = { Text(stringResource(R.string.builder_name_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            isError = state.nameError,
            supportingText = if (state.nameError) ({ Text(stringResource(R.string.builder_name_required)) }) else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.builder_actions_title), style = MaterialTheme.typography.titleMedium)
        if (state.actions.isEmpty()) {
            Text(
                stringResource(R.string.builder_empty_actions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.actions.forEachIndexed { index, action ->
            ActionRow(index, action, state, actions)
        }
        AddActionButton(state, actions.onAddAction)

        if (state.actions.isNotEmpty()) {
            TargetSelector(state.target, actions.onTargetSelected)
            SizeBudgetMeter(
                budget = state.analysis.budget,
                targetName = targetName(state.target),
                suggestedChipName = state.analysis.suggestedChip?.displayName,
            )
        }

        SaveAndWrite(state, actions)
    }
}

@Composable
private fun ActionRow(index: Int, action: TaskAction, state: TaskBuilderUiState, actions: TaskBuilderActions) {
    val description = TaskText.describe(LocalResources.current, action)
    val invalid = state.analysis.invalid[index]
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.builder_action_number, index + 1), style = MaterialTheme.typography.labelMedium)
            Text(description, style = MaterialTheme.typography.bodyLarge)
            RiskBadge(action.risk)
            if (invalid != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(R.string.builder_action_invalid) + ": " + stringResource(TaskText.blocked(invalid)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { actions.onMoveAction(index, -1) }, enabled = index > 0) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.write_move_up))
                }
                IconButton(onClick = { actions.onMoveAction(index, +1) }, enabled = index < state.actions.lastIndex) {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(R.string.write_move_down))
                }
                IconButton(onClick = { actions.onEditAction(index) }) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.write_edit_record))
                }
                IconButton(onClick = { actions.onRemoveAction(index) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.write_remove_record))
                }
            }
        }
    }
}

@Composable
private fun AddActionButton(state: TaskBuilderUiState, onAdd: () -> Unit) {
    OutlinedButton(onClick = onAdd, enabled = !state.isFull, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = null)
        Text(stringResource(R.string.builder_add_action), modifier = Modifier.padding(start = 8.dp))
    }
    if (state.isFull) {
        Text(stringResource(R.string.builder_full), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TargetSelector(target: WriteTarget, onSelect: (WriteTarget) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.write_target_title), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NtagModel.entries.forEach { model ->
                FilterChip(
                    selected = target == WriteTarget.Chip(model),
                    onClick = { onSelect(WriteTarget.Chip(model)) },
                    label = { Text(model.displayName) },
                )
            }
            (target as? WriteTarget.LastTag)?.let { last ->
                FilterChip(
                    selected = true,
                    onClick = { onSelect(last) },
                    label = { Text(stringResource(R.string.write_target_detected, last.maxSize)) },
                )
            }
        }
    }
}

@Composable
private fun SaveAndWrite(state: TaskBuilderUiState, actions: TaskBuilderActions) {
    val reason = when {
        state.availability != NfcAvailability.ENABLED -> R.string.write_blocked_nfc
        state.analysis.block == TaskBlock.NO_ACTIONS -> R.string.builder_blocked_empty
        state.analysis.block == TaskBlock.INVALID_ACTION -> R.string.builder_blocked_invalid
        state.analysis.block == TaskBlock.ENCODE_ERROR -> R.string.builder_blocked_encode
        state.analysis.block == TaskBlock.OVER_CAPACITY -> R.string.builder_blocked_over
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = actions.onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.builder_save))
        }
        Button(onClick = actions.onWrite, enabled = state.canWrite, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.builder_write))
        }
        // A greyed-out button always says why, right beneath it.
        if (reason != null) {
            Text(stringResource(reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(stringResource(R.string.builder_overwrite_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
