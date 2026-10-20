package com.tagforge.app.feature.productivity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.tagforge.app.R
import com.tagforge.app.infrastructure.android.QrImageExporter
import com.tagforge.app.infrastructure.android.QrMatrix

private val MaxCodeWidth = 360.dp

/** A large, sharp QR with Share (PNG) and, from Android 10, Save to the gallery. A code can carry a Wi-Fi password, so it says so. */
@Composable
fun QrCodeCard(matrix: QrMatrix, title: String, onShare: () -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        QrCodeView(matrix, stringResource(R.string.qr_code_description), Modifier.widthIn(max = MaxCodeWidth))
        Text(stringResource(R.string.qr_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Share, contentDescription = null)
            Text(stringResource(R.string.qr_share), modifier = Modifier.padding(start = 8.dp))
        }
        if (QrImageExporter.canSaveToGallery) {
            OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Download, contentDescription = null)
                Text(stringResource(R.string.qr_save), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
