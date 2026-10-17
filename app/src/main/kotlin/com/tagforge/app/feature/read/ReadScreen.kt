package com.tagforge.app.feature.read

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.app.ui.components.NfcStatusBanner
import com.tagforge.app.ui.components.RadarPulse
import com.tagforge.app.ui.components.StatusText
import com.tagforge.app.ui.text.TagErrorText
import com.tagforge.app.ui.text.messageText
import com.tagforge.app.ui.text.titleText
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagInfo

/**
 * Presentational Read screen: takes state and lambdas only, so it renders without a ViewModel, repository or
 * NfcAdapter (AS-05).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadScreen(
    state: ReadUiState,
    snackbarHostState: SnackbarHostState,
    onOpenNfcSettings: () -> Unit,
    onRetry: () -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.read_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                ReadUiState.Unsupported -> Banner(NfcAvailability.UNSUPPORTED, onOpenNfcSettings)
                ReadUiState.Disabled -> Banner(NfcAvailability.DISABLED, onOpenNfcSettings)
                ReadUiState.Waiting -> Listening(fast = false)
                is ReadUiState.Reading -> Listening(fast = true)
                is ReadUiState.Success -> Column(Modifier.fillMaxSize()) {
                    Text(
                        stringResource(R.string.scan_next_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    ReadDetail(state.scan)
                }
                is ReadUiState.Error -> ErrorPanel(state.error, onRetry)
                is ReadUiState.Protected -> ProtectedPanel(state.tag, onUnlock)
            }
        }
    }
}

@Composable
private fun Banner(availability: NfcAvailability, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) { NfcStatusBanner(availability, onOpenSettings) }
}

@Composable
private fun Listening(fast: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        RadarPulse(fast = fast)
        if (fast) {
            StatusText(stringResource(R.string.scan_working_title), stringResource(R.string.scan_working_hint))
        } else {
            StatusText(stringResource(R.string.scan_waiting_title), stringResource(R.string.scan_waiting_hint))
        }
    }
}

/** The error sits next to its retry control, not in a transient toast (AS-06). */
@Composable
private fun ErrorPanel(error: TagError, onRetry: () -> Unit) {
    val text = TagErrorText.of(error)
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        StatusText(title = text.titleText() ?: stringResource(R.string.scan_failure_title), supporting = text.messageText())
        Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
    }
}

@Composable
private fun ProtectedPanel(tag: TagInfo?, onUnlock: () -> Unit) {
    val rows = if (tag != null) tagInfoRows(tag, hasMessage = false) else emptyList()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                StatusText(stringResource(R.string.read_protected_title), stringResource(R.string.read_protected_body))
                OutlinedButton(onClick = onUnlock) { Text(stringResource(R.string.read_unlock)) }
            }
        }
        itemsIndexed(rows) { _, (label, value) ->
            Column(Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Start)
            }
        }
    }
}
