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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.components.BackNavigationIcon

/** Presentational QR screen: scan or read a code, then create one. While [QrUiState.scanning] the camera takes the whole body. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScreen(
    state: QrUiState,
    read: QrReadActions,
    create: QrCreateActions,
    onScanned: (String) -> Unit,
    onCloseScanner: () -> Unit,
    cameraDenied: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.scanning) R.string.qr_scan_title else R.string.qr_title)) },
                navigationIcon = { BackNavigationIcon(if (state.scanning) onCloseScanner else onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.scanning) {
            QrCameraScan(onScanned = onScanned, modifier = Modifier.padding(padding))
        } else {
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.generated != null && state.fromTag) {
                    QrCodeCard(state.generated, stringResource(R.string.qr_generated_title), create.onShare, create.onSave)
                    HorizontalDivider(Modifier.fillMaxWidth())
                }
                QrReadSection(state, read, cameraDenied)
                HorizontalDivider(Modifier.fillMaxWidth())
                QrCreateSection(state, create)
                if (state.generated != null && !state.fromTag) {
                    QrCodeCard(state.generated, stringResource(R.string.qr_created_title), create.onShare, create.onSave)
                } else if (state.generationFailed) {
                    Text(stringResource(R.string.qr_generation_failed), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
