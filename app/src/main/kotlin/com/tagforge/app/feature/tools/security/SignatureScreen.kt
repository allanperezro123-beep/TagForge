package com.tagforge.app.feature.tools.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.feature.tools.ScanErrorPanel
import com.tagforge.app.feature.tools.ScanListening
import com.tagforge.app.feature.tools.ToolReading
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.app.ui.components.BackNavigationIcon
import com.tagforge.app.ui.components.NfcStatusBanner
import com.tagforge.domain.security.SignatureReport

/** NS-07: the 32 signature bytes next to the UID, always labelled "not verified". Presentational. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureScreen(
    state: SignatureUiState,
    onBack: () -> Unit,
    onRescan: () -> Unit,
    onCopy: (String) -> Unit,
    onOpenNfcSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.signature_title)) }, navigationIcon = { BackNavigationIcon(onBack) }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.availability != NfcAvailability.ENABLED) NfcStatusBanner(state.availability, onOpenNfcSettings, Modifier.padding(16.dp))
            when (val reading = state.reading) {
                ToolReading.Waiting -> ScanListening(false, R.string.signature_waiting_title, R.string.signature_waiting_hint)
                is ToolReading.Reading -> ScanListening(true, R.string.signature_waiting_title, R.string.signature_waiting_hint)
                is ToolReading.Error -> ScanErrorPanel(reading.error, onRescan)
                is ToolReading.Result -> SignatureResult(reading.value, onRescan, onCopy)
            }
        }
    }
}

@Composable
private fun SignatureResult(report: SignatureReport, onRescan: () -> Unit, onCopy: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Labelled(R.string.signature_uid, report.uid.toHex(":"))
        Labelled(R.string.signature_value, report.signature.toHex(" "))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Start))
            Text(stringResource(R.string.signature_status_not_verified), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.signature_status_explain), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = { onCopy(report.signature.toHex()) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.signature_copy)) }
        OutlinedButton(onClick = onRescan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.tools_scan_another)) }
    }
}

@Composable
private fun Labelled(labelRes: Int, value: String) {
    Column {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
    }
}
