package com.tagforge.app.feature.productivity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.components.BackNavigationIcon

/**
 * Presentational import/export screen (HT-04). The three buttons only ask for a file; the container opens the system
 * picker. While [ImportExportUiState.busy] the buttons are off and a progress bar is shown; the outcome is a live
 * region so a screen reader announces it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    state: ImportExportUiState,
    onBack: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImport: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_export_title)) },
                navigationIcon = { BackNavigationIcon(onBack) },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.import_export_intro), style = MaterialTheme.typography.bodyMedium)

            if (state.busy) {
                Text(
                    stringResource(R.string.import_export_working),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.message?.let { message -> MessageCard(message, onMessageShown) }

            ActionCard(
                title = stringResource(R.string.import_export_export_json),
                body = stringResource(R.string.import_export_export_json_desc),
                enabled = !state.busy,
                onClick = onExportJson,
                primary = true,
            )
            ActionCard(
                title = stringResource(R.string.import_export_export_csv),
                body = stringResource(R.string.import_export_export_csv_desc),
                enabled = !state.busy,
                onClick = onExportCsv,
            )
            ActionCard(
                title = stringResource(R.string.import_export_import),
                body = stringResource(R.string.import_export_import_desc),
                enabled = !state.busy,
                onClick = onImport,
            )
        }
    }
}

@Composable
private fun MessageCard(message: ImportExportMessage, onDismiss: () -> Unit) {
    val failed = message !is ImportExportMessage.Exported && message !is ImportExportMessage.Imported
    Card(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(
            containerColor = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // The word, not just the colour, says whether it worked.
            Text(
                stringResource(if (failed) R.string.import_export_result_failed else R.string.import_export_result_ok),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(message.text(), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    }
}

@Composable
private fun ActionCard(title: String, body: String, enabled: Boolean, onClick: () -> Unit, primary: Boolean = false) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (primary) {
                Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(title) }
            } else {
                OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(title) }
            }
        }
    }
}
