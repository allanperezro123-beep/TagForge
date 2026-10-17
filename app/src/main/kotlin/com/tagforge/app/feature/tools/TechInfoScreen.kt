package com.tagforge.app.feature.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.app.ui.components.BackNavigationIcon
import com.tagforge.app.ui.components.NfcStatusBanner

/** "Tag technologies" (TT-05): what each technology of the tapped tag reports. Presentational. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechInfoScreen(
    state: TechInfoUiState,
    onBack: () -> Unit,
    onRescan: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tech_title)) },
                navigationIcon = { BackNavigationIcon(onBack) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.availability != NfcAvailability.ENABLED) {
                NfcStatusBanner(state.availability, onOpenNfcSettings, Modifier.padding(16.dp))
            }
            when (val reading = state.reading) {
                ToolReading.Waiting -> ScanListening(false, R.string.tech_waiting_title, R.string.tech_waiting_hint)
                is ToolReading.Reading -> ScanListening(true, R.string.tech_waiting_title, R.string.tech_waiting_hint)
                is ToolReading.Error -> ScanErrorPanel(reading.error, onRescan)
                is ToolReading.Result -> {
                    TechSections(reading.value, Modifier.weight(1f))
                    OutlinedButton(onClick = onRescan, modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.tools_scan_another))
                    }
                }
            }
        }
    }
}
