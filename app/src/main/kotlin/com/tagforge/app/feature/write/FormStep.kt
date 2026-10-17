package com.tagforge.app.feature.write

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.feature.write.forms.RecordForm
import com.tagforge.app.ui.components.RecordCard
import com.tagforge.app.ui.components.SizeBudgetMeter
import com.tagforge.app.ui.theme.HexTextStyle
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.SpecField

/**
 * Step 2: the form for one record, a live preview of exactly what will be encoded (decoded view plus the raw bytes)
 * and the size meter, so every keystroke shows its cost on the tag.
 */
@Composable
fun FormStep(
    state: WriteUiState,
    editing: EditingRecord,
    onDraftChanged: (RecordSpec) -> Unit,
    onFieldTouched: (SpecField) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = editing.spec.kind()
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.Icon(kind.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(kind.titleRes()), style = MaterialTheme.typography.headlineSmall)
        }

        RecordForm(editing.spec, editing.visibleErrors, onDraftChanged, onFieldTouched)

        LivePreview(state)

        SizeBudgetMeter(
            budget = state.analysis.budget,
            targetName = targetName(state.target),
            suggestedChipName = state.suggestedChip?.displayName,
        )

        if (editing.showAllErrors && !editing.isValid) {
            Text(
                stringResource(R.string.write_fix_fields),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (editing.index == null) R.string.write_add_to_message else R.string.write_save_changes))
        }
    }
}

@Composable
private fun LivePreview(state: WriteUiState) {
    var showBytes by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.write_preview_title), style = MaterialTheme.typography.titleMedium)
        }
        val parsed = state.previewParsed
        if (parsed == null) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.write_preview_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            // The same card the Read screen shows, so what you see here is what a reader will see.
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) { RecordCard(parsed, index = state.editing?.index ?: state.records.size) }
            val hex = state.previewHex
            if (hex != null) {
                TextButton(onClick = { showBytes = !showBytes }) {
                    Text(stringResource(if (showBytes) R.string.write_preview_hide_hex else R.string.write_preview_show_hex))
                }
                if (showBytes) {
                    Text(
                        stringResource(R.string.write_preview_hex_title) + " · " +
                            stringResource(R.string.write_preview_bytes, hex.count { it == ' ' } + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectionContainer { Text(hex, style = HexTextStyle) }
                }
            }
        }
    }
}
