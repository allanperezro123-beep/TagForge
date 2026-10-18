package com.tagforge.app.feature.productivity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.feature.write.kind
import com.tagforge.app.feature.write.titleRes
import com.tagforge.domain.ndef.spec.RecordSpec

/** Everything the read half of the QR screen can ask for. */
data class QrReadActions(
    val onChooseImage: () -> Unit,
    val onPasteChanged: (String) -> Unit,
    val onPasteSubmit: () -> Unit,
    val onUse: () -> Unit,
    val onDismiss: () -> Unit,
)

/** Reading a code: from a picked picture or from pasted text. There is deliberately no camera option. */
@Composable
fun QrReadSection(state: QrUiState, actions: QrReadActions, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.qr_read_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.qr_read_intro), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = actions.onChooseImage, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.qr_choose_image))
        }
        OutlinedTextField(
            value = state.pasteText,
            onValueChange = actions.onPasteChanged,
            label = { Text(stringResource(R.string.qr_paste_label)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = actions.onPasteSubmit, enabled = state.canSubmitPaste, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.qr_paste_read))
        }
        ResultArea(state.result, actions)
    }
}

@Composable
private fun ResultArea(result: QrReadResult, actions: QrReadActions) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when (result) {
            QrReadResult.Idle -> Unit
            QrReadResult.Reading -> {
                Text(stringResource(R.string.qr_reading))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            QrReadResult.NotFound -> Problem(R.string.qr_not_found)
            QrReadResult.Unreadable -> Problem(R.string.qr_unreadable)
            is QrReadResult.Found -> FoundCard(result, actions)
        }
    }
}

@Composable
private fun Problem(message: Int) {
    Text(stringResource(message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun FoundCard(found: QrReadResult.Found, actions: QrReadActions) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.qr_result_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.qr_result_kind, stringResource(found.record.kind().titleRes())))
            // A Wi-Fi password is shown only in the wizard, never in this preview.
            val preview = (found.record as? RecordSpec.Wifi)?.let { stringResource(R.string.qr_result_wifi, it.ssid) }
                ?: found.text.take(PREVIEW_CHARS)
            Text(preview, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions.onUse) { Text(stringResource(R.string.qr_use)) }
                TextButton(onClick = actions.onDismiss) { Text(stringResource(R.string.common_close)) }
            }
        }
    }
}

private const val PREVIEW_CHARS = 300
