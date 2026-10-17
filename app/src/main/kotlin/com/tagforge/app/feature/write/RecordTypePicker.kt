package com.tagforge.app.feature.write

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tagforge.app.R

private const val COLUMNS = 2

/**
 * Step 1: what to write, grouped so the choice is quick to scan. Every tile says what the tag will DO when tapped,
 * not just what it contains.
 */
@Composable
fun RecordTypePicker(
    onSelect: (RecordKind) -> Unit,
    modifier: Modifier = Modifier,
    hasRecords: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.write_step_type), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.write_step_type_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RecordGroup.entries.forEach { group ->
            Text(
                stringResource(group.titleRes()),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            RecordKind.entries.filter { it.group == group }.chunked(COLUMNS).forEach { rowKinds ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowKinds.forEach { kind -> TypeTile(kind, onClick = { onSelect(kind) }, modifier = Modifier.weight(1f)) }
                    repeat(COLUMNS - rowKinds.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (hasRecords) Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun TypeTile(kind: RecordKind, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(kind.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(kind.titleRes()), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(kind.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
