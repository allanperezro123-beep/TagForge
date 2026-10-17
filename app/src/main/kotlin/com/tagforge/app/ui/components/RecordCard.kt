package com.tagforge.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.theme.HexTextStyle
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.ParsedRecord

private const val PREVIEW_BYTES = 64

/**
 * One NDEF record: decoded content first, with a raw-hex expander so nothing is ever hidden from a power user.
 * Stateless apart from the expander toggle; it takes the already-parsed record and nothing else.
 */
@Composable
fun RecordCard(parsed: ParsedRecord, index: Int, modifier: Modifier = Modifier) {
    var showRaw by rememberSaveable(index) { mutableStateOf(false) }

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(typeLabel(parsed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("#${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { RecordContent(parsed) }
            }
            TextButton(onClick = { showRaw = !showRaw }) {
                Text(stringResource(if (showRaw) R.string.record_hide_raw else R.string.record_show_raw))
            }
            if (showRaw) RawRecord(parsed.record)
        }
    }
}

@Composable
private fun typeLabel(parsed: ParsedRecord): String = stringResource(
    when (parsed) {
        is ParsedRecord.Text -> R.string.record_type_text
        is ParsedRecord.Uri -> R.string.record_type_uri
        is ParsedRecord.Mime -> R.string.record_type_mime
        is ParsedRecord.External -> R.string.record_type_external
        is ParsedRecord.Unknown -> R.string.record_type_unknown
    },
)

@Composable
private fun RecordContent(parsed: ParsedRecord) {
    when (parsed) {
        is ParsedRecord.Text -> {
            Text(parsed.text, style = MaterialTheme.typography.bodyLarge)
            FieldRow(stringResource(R.string.record_field_language), parsed.languageCode)
        }
        is ParsedRecord.Uri -> {
            Text(parsed.uri, style = MaterialTheme.typography.bodyLarge)
            if (!parsed.prefixKnown) {
                Text(stringResource(R.string.record_unknown_prefix), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        is ParsedRecord.Mime -> {
            FieldRow(stringResource(R.string.record_field_type), parsed.mimeType)
            Text(mimePreview(parsed), style = if (parsed.mimeType.startsWith("text/")) MaterialTheme.typography.bodyLarge else HexTextStyle)
            FieldRow(stringResource(R.string.record_field_size), stringResource(R.string.record_size_bytes, parsed.payload.size))
        }
        is ParsedRecord.External -> {
            FieldRow(stringResource(R.string.record_field_type), "${parsed.domain}:${parsed.type}")
            FieldRow(stringResource(R.string.record_field_size), stringResource(R.string.record_size_bytes, parsed.payload.size))
        }
        is ParsedRecord.Unknown -> {
            FieldRow(stringResource(R.string.record_field_tnf), parsed.record.tnf.name)
            FieldRow(stringResource(R.string.record_field_size), stringResource(R.string.record_size_bytes, parsed.record.payload.size))
        }
    }
}

private fun mimePreview(mime: ParsedRecord.Mime): String =
    if (mime.mimeType.startsWith("text/")) {
        String(mime.payload.toByteArray(), Charsets.UTF_8)
    } else {
        previewHex(mime.payload)
    }

private fun previewHex(bytes: Bytes): String {
    val head = bytes.slice(0, minOf(bytes.size, PREVIEW_BYTES)).toHex(" ")
    return if (bytes.size > PREVIEW_BYTES) "$head …" else head
}

@Composable
private fun RawRecord(record: NdefRecord) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldRow(stringResource(R.string.record_field_tnf), "${record.tnf.name} (${record.tnf.code})")
            FieldRow(stringResource(R.string.record_field_type), record.type.toHex(" ").ifEmpty { "-" })
            Text(stringResource(R.string.record_field_payload), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(record.payload.toHex(" ").ifEmpty { "-" }, style = HexTextStyle)
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Sensitive value (a WiFi key from Phase 2 on) that stays masked until tapped. Its state is announced, not colour-coded.
 */
@Composable
fun SecretValue(value: String, modifier: Modifier = Modifier) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    val hint = stringResource(if (revealed) R.string.record_secret_shown else R.string.record_secret_hidden)
    Text(
        text = if (revealed) value else "•".repeat(MASK_LENGTH),
        style = HexTextStyle,
        modifier = modifier
            .clickable(role = Role.Button) { revealed = !revealed }
            .semantics { stateDescription = hint },
    )
}

private const val MASK_LENGTH = 8
