package com.tagforge.app.feature.productivity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.exchange.BatchBlock
import com.tagforge.domain.exchange.BatchSettings
import com.tagforge.domain.ntag.NtagModel

/** Everything the setup step can do; plain lambdas so the screen stays previewable. */
data class BatchSetupActions(
    val onTemplateSelected: (Long) -> Unit,
    val onStartChanged: (String) -> Unit,
    val onStepChanged: (String) -> Unit,
    val onPadChanged: (String) -> Unit,
    val onTotalChanged: (String) -> Unit,
    val onAarChanged: (Boolean) -> Unit,
    val onTargetSelected: (NtagModel) -> Unit,
    val onStart: () -> Unit,
)

@Composable
fun BatchSetupContent(state: BatchUiState, actions: BatchSetupActions, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.batch_intro), style = MaterialTheme.typography.bodyMedium)

        TemplatePicker(state, actions.onTemplateSelected)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(state.startText, actions.onStartChanged, stringResource(R.string.batch_first_number), Modifier.weight(1f))
            NumberField(state.stepText, actions.onStepChanged, stringResource(R.string.batch_step), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(state.padText, actions.onPadChanged, stringResource(R.string.batch_digits), Modifier.weight(1f))
            NumberField(state.totalText, actions.onTotalChanged, stringResource(R.string.batch_total), Modifier.weight(1f))
        }

        Text(stringResource(R.string.batch_target), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NtagModel.entries.forEach { model ->
                val selected = state.target == model
                FilterChip(
                    selected = selected,
                    onClick = { actions.onTargetSelected(model) },
                    label = { Text(model.displayName) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Outlined.Done, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.batch_add_aar), Modifier.weight(1f))
            Switch(checked = state.includeAar, onCheckedChange = actions.onAarChanged)
        }

        BatchPreview(state)

        Button(onClick = actions.onStart, enabled = state.canStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.batch_start))
        }
        if (state.availability != NfcAvailability.ENABLED) {
            Text(stringResource(R.string.batch_nfc_off), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatePicker(state: BatchUiState, onSelected: (Long) -> Unit) {
    if (state.templates.isEmpty()) {
        Text(stringResource(R.string.batch_no_templates), style = MaterialTheme.typography.bodyMedium)
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val selectedName = state.templates.firstOrNull { it.id == state.selectedTemplateId }?.name.orEmpty()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.batch_template)) },
            placeholder = { Text(stringResource(R.string.batch_template_pick)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.templates.forEach { template ->
                DropdownMenuItem(
                    text = { Text(template.name) },
                    onClick = {
                        expanded = false
                        onSelected(template.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(NUMBER_MAX_CHARS)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun BatchPreview(state: BatchUiState) {
    val analysis = state.analysis
    val settings = state.settings
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (analysis.block == null || analysis.block == BatchBlock.OVER_CAPACITY) {
            val first = settings.start.toString().padStart(settings.pad, '0')
            val last = (settings.start + (settings.total - 1) * settings.step).toString().padStart(settings.pad, '0')
            Text(stringResource(R.string.batch_preview, first, last), style = MaterialTheme.typography.bodyMedium)
        }
        analysis.budget?.let {
            Text(stringResource(R.string.batch_size, it.used, it.capacity), style = MaterialTheme.typography.bodyMedium)
        }
        analysis.block?.let { block ->
            Text(block.text(state), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (state.records.isNotEmpty() && !state.usesCounter) {
            Text(stringResource(R.string.batch_no_token), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BatchBlock.text(state: BatchUiState): String = when (this) {
    BatchBlock.NO_RECORDS -> stringResource(R.string.batch_block_no_records)
    BatchBlock.INVALID_RECORD -> stringResource(R.string.batch_block_invalid_record)
    BatchBlock.BAD_COUNT -> stringResource(R.string.batch_block_bad_count, BatchSettings.MAX_TOTAL)
    BatchBlock.BAD_STEP -> stringResource(R.string.batch_block_bad_step)
    BatchBlock.BAD_PAD -> stringResource(R.string.batch_block_bad_pad, BatchSettings.MAX_PAD)
    BatchBlock.BAD_START -> stringResource(R.string.batch_block_bad_start)
    BatchBlock.COUNTER_OVERFLOW -> stringResource(R.string.batch_block_overflow)
    BatchBlock.OVER_CAPACITY -> stringResource(R.string.batch_block_over_capacity, state.analysis.budget?.overBy ?: 0, state.target.displayName)
}

private const val NUMBER_MAX_CHARS = 12
