package com.tagforge.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.infrastructure.nfc.NfcAvailability

/**
 * Explains why scanning is unavailable. Unsupported is a read-only explanation; Disabled offers the NFC settings.
 * Renders nothing when NFC is enabled. State is conveyed by icon AND text, never colour alone.
 */
@Composable
fun NfcStatusBanner(
    availability: NfcAvailability,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (availability == NfcAvailability.ENABLED) return
    val unsupported = availability == NfcAvailability.UNSUPPORTED

    Card(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(
            containerColor = if (unsupported) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (unsupported) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (unsupported) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber, contentDescription = null)
                Text(
                    stringResource(if (unsupported) R.string.nfc_unsupported_title else R.string.nfc_disabled_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                stringResource(if (unsupported) R.string.nfc_unsupported_body else R.string.nfc_disabled_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!unsupported) {
                Button(onClick = onOpenSettings) { Text(stringResource(R.string.nfc_open_settings)) }
            }
        }
    }
}
