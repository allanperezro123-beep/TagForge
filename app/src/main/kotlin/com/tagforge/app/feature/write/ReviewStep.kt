package com.tagforge.app.feature.write

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.components.EmptyState
import com.tagforge.app.ui.components.RecordCard
import com.tagforge.app.ui.components.SizeBudgetMeter
import com.tagforge.app.ui.theme.HexTextStyle
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.write.WriteBlock

/** Step 3: the whole message, its cost on the chosen tag, and the one button that writes it. */
@Composable
fun ReviewStep(
    state: WriteUiState,
    actions: WriteActions,
    modifier: Modifier = Modifier,
) {
    if (state.records.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Edit,
            title = stringResource(R.string.write_empty_title),
            body = stringResource(R.string.write_empty_body),
            modifier = modifier,
            action = { Button(onClick = actions.onAddRecord) { Text(stringResource(R.string.write_add_record)) } },
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.write_review_title), style = MaterialTheme.typography.headlineSmall)

        state.records.forEachIndexed { index, _ ->
            RecordRow(state, index, actions)
        }

        OutlinedButton(onClick = actions.onAddRecord, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = null)
            Text(stringResource(R.string.write_add_record), modifier = Modifier.padding(start = 8.dp))
        }

        AarSwitch(state.includeAar, actions.onIncludeAarChanged)

        TargetSelector(state, actions.onTargetSelected)
        SizeBudgetMeter(
            budget = state.analysis.budget,
            targetName = targetName(state.target),
            suggestedChipName = state.suggestedChip?.displayName,
        )
        MessageBytes(state.messageHex)

        WriteButton(state, actions.onWrite)

        state.qrPayload?.let { payload ->
            OutlinedButton(onClick = { actions.onShowQr(payload) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.QrCode2, contentDescription = null)
                Text(stringResource(R.string.write_show_qr), modifier = Modifier.padding(start = 8.dp))
            }
        }

        TextButton(
            onClick = actions.onSaveTemplate,
            enabled = state.analysis.invalid.isEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.BookmarkAdd, contentDescription = null)
            Text(stringResource(R.string.write_save_template), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun RecordRow(state: WriteUiState, index: Int, actions: WriteActions) {
    val parsed = state.recordPreviews.getOrNull(index)
    val errors = state.analysis.invalid[index]
    val lastIndex = state.records.lastIndex

    val rowActions: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { actions.onMoveRecord(index, -1) }, enabled = index > 0) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.write_move_up))
            }
            IconButton(onClick = { actions.onMoveRecord(index, +1) }, enabled = index < lastIndex) {
                Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(R.string.write_move_down))
            }
            IconButton(onClick = { actions.onEditRecord(index) }) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.write_edit_record))
            }
            IconButton(onClick = { actions.onRemoveRecord(index) }) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.write_remove_record))
            }
        }
    }

    if (parsed != null && errors == null) {
        RecordCard(parsed, index, actions = rowActions)
    } else {
        // Incomplete or invalid (for example an old template): say so next to the record, with edit and remove at hand.
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(state.records[index].kind().titleRes()), style = MaterialTheme.typography.titleMedium)
                }
                Text(stringResource(R.string.write_record_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                rowActions()
            }
        }
    }
}

@Composable
private fun AarSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        headlineContent = { Text(stringResource(R.string.write_include_aar)) },
        supportingContent = { Text(stringResource(R.string.write_include_aar_desc)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun TargetSelector(state: WriteUiState, onSelect: (WriteTarget) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.write_target_title), style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NtagModel.entries.forEach { model ->
                FilterChip(
                    selected = state.target == WriteTarget.Chip(model),
                    onClick = { onSelect(WriteTarget.Chip(model)) },
                    label = { Text(model.displayName) },
                )
            }
            state.lastTagCapacity?.let { size ->
                FilterChip(
                    selected = state.target is WriteTarget.LastTag,
                    onClick = { onSelect(WriteTarget.LastTag(size)) },
                    label = { Text(stringResource(R.string.write_target_detected, size)) },
                )
            }
        }
    }
}

@Composable
private fun MessageBytes(hex: String?) {
    if (hex == null) return
    var show by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton(onClick = { show = !show }) {
            Text(stringResource(if (show) R.string.write_preview_hide_hex else R.string.write_preview_show_hex))
        }
        if (show) SelectionContainer { Text(hex, style = HexTextStyle) }
    }
}

@Composable
private fun WriteButton(state: WriteUiState, onWrite: () -> Unit) {
    val reason = when {
        state.availability != com.tagforge.app.infrastructure.nfc.NfcAvailability.ENABLED -> R.string.write_blocked_nfc
        state.analysis.block == WriteBlock.NO_RECORDS -> R.string.write_blocked_empty
        state.analysis.block == WriteBlock.INVALID_RECORD -> R.string.write_blocked_invalid
        state.analysis.block == WriteBlock.OVER_CAPACITY -> R.string.write_blocked_over
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = onWrite, enabled = state.canWrite, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.write_action))
        }
        // The reason sits right under the disabled button: a greyed-out control must always explain itself.
        if (reason != null) {
            Text(stringResource(reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
