package com.tagforge.app.feature.productivity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.components.BackNavigationIcon

/** Presentational QR screen (PR-02): the code for the wizard message on top, reading a code below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScreen(state: QrUiState, actions: QrReadActions, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qr_title)) },
                navigationIcon = { BackNavigationIcon(onBack) },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.generated != null) {
                Text(stringResource(R.string.qr_generated_title), style = MaterialTheme.typography.titleMedium)
                QrCodeView(state.generated, stringResource(R.string.qr_code_description))
                Text(
                    stringResource(R.string.qr_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.fillMaxWidth())
            } else if (state.generationFailed) {
                Text(stringResource(R.string.qr_generation_failed), color = MaterialTheme.colorScheme.error)
            }
            QrReadSection(state, actions)
        }
    }
}
