package com.tagforge.app.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.app.ui.components.BackNavigationIcon
import com.tagforge.app.ui.components.NfcStatusBanner
import com.tagforge.domain.ntag.Region
import com.tagforge.domain.tools.DumpReport

data class DumpActions(
    val onBack: () -> Unit,
    val onRescan: () -> Unit,
    val onLayoutChanged: (DumpLayout) -> Unit,
    val onPageSelected: (Int?) -> Unit,
    val onCopy: () -> Unit,
    val onShareText: () -> Unit,
    val onShareBinary: () -> Unit,
    val onOpenNfcSettings: () -> Unit,
)

/** Annotated memory dump (TT-03) with copy and share (TT-06). Presentational: state and lambdas only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DumpScreen(
    state: DumpUiState,
    actions: DumpActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val report = state.report
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dump_title)) },
                navigationIcon = { BackNavigationIcon(actions.onBack) },
                actions = { if (report != null) ExportActions(actions) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.availability != NfcAvailability.ENABLED) {
                NfcStatusBanner(state.availability, actions.onOpenNfcSettings, Modifier.padding(16.dp))
            }
            when (val reading = state.reading) {
                ToolReading.Waiting -> ScanListening(fast = false, waitingTitle = R.string.dump_waiting_title, waitingHint = R.string.dump_waiting_hint)
                is ToolReading.Reading -> ScanListening(fast = true, waitingTitle = R.string.dump_waiting_title, waitingHint = R.string.dump_waiting_hint)
                is ToolReading.Error -> ScanErrorPanel(reading.error, actions.onRescan)
                is ToolReading.Result -> ResultList(state, reading.value, actions)
            }
        }
    }

    val selected = state.selectedPage
    if (report != null && selected != null) {
        val row = DumpRows.build(report.dump, state.layout).firstOrNull { selected in it.pages }
        if (row != null) PageExplanationSheet(row, onDismiss = { actions.onPageSelected(null) })
    }
}

@Composable
private fun ExportActions(actions: DumpActions) {
    var menuOpen by remember { mutableStateOf(false) }
    IconButton(onClick = actions.onCopy) {
        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.dump_copy))
    }
    IconButton(onClick = { menuOpen = true }) {
        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.dump_share))
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.dump_share_text)) },
            onClick = { menuOpen = false; actions.onShareText() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.dump_share_binary)) },
            onClick = { menuOpen = false; actions.onShareBinary() },
        )
    }
}

@Composable
private fun ResultList(state: DumpUiState, report: DumpReport, actions: DumpActions) {
    val rows = DumpRows.build(report.dump, state.layout)
    val regionsPresent = Region.entries.filter { region -> report.dump.annotations.any { region in it.regions } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        R.string.dump_header,
                        report.info.chipName ?: stringResource(R.string.common_unknown),
                        report.info.displayUid.toHex(":"),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (regionsPresent.isNotEmpty()) RegionLegend(regionsPresent) else Text(
                    stringResource(R.string.dump_no_annotations),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LayoutToggle(state.layout, actions.onLayoutChanged)
                Text(
                    stringResource(R.string.dump_tap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(rows, key = { it.startPage }) { row ->
            DumpRowItem(row = row, layout = state.layout, onClick = { actions.onPageSelected(row.startPage) })
        }
        item(key = "footer") {
            report.dump.unreadable?.let { range ->
                Text(
                    stringResource(R.string.hex_unreadable_range, range.first, range.last),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            OutlinedButton(onClick = actions.onRescan, modifier = Modifier.padding(vertical = 12.dp)) {
                Text(stringResource(R.string.dump_scan_again))
            }
        }
    }
}
