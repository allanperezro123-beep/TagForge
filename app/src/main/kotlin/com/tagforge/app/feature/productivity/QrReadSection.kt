package com.tagforge.app.feature.productivity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tagforge.app.R

/** Everything the read half of the QR screen can ask for. */
data class QrReadActions(
    val onScan: () -> Unit,
    val onChooseImage: () -> Unit,
    val onPasteChanged: (String) -> Unit,
    val onPasteSubmit: () -> Unit,
    val onUse: () -> Unit,
    val onOpen: () -> Unit,
    val onCopy: () -> Unit,
    val onSaveTemplate: (String) -> Unit,
    val onDismiss: () -> Unit,
    val onOpenAppSettings: () -> Unit,
)

/** Reading a code: live with the camera (asked for only when the user taps Scan), from a picture, or from pasted text. */
@Composable
fun QrReadSection(state: QrUiState, actions: QrReadActions, cameraDenied: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.qr_read_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.qr_read_intro), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = actions.onScan, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
            Text(stringResource(R.string.qr_scan_button), modifier = Modifier.padding(start = 8.dp))
        }
        if (cameraDenied) CameraDenied(actions.onOpenAppSettings)
        OutlinedButton(onClick = actions.onChooseImage, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
            Text(stringResource(R.string.qr_choose_image), modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedTextField(
            value = state.pasteText,
            onValueChange = actions.onPasteChanged,
            label = { Text(stringResource(R.string.qr_paste_label)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = actions.onPasteSubmit, enabled = state.canSubmitPaste, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.qr_paste_read))
        }
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            when (val result = state.result) {
                QrReadResult.Idle -> Unit
                QrReadResult.Reading -> {
                    Text(stringResource(R.string.qr_reading))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                QrReadResult.NotFound -> Problem(R.string.qr_not_found)
                QrReadResult.Unreadable -> Problem(R.string.qr_unreadable)
                is QrReadResult.Found -> QrResultCard(result, actions)
            }
        }
    }
}

@Composable
private fun CameraDenied(onOpenAppSettings: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.qr_camera_denied), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.qr_open_app_settings)) }
        }
    }
}

@Composable
private fun Problem(message: Int) {
    Text(stringResource(message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}
