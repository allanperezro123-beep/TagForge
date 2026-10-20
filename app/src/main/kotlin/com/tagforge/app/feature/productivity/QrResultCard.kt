package com.tagforge.app.feature.productivity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.domain.exchange.QrContent

private const val PREVIEW_CHARS = 300
private const val LINK_LINES = 4

/**
 * What a scanned code says in plain words, then the things the person may do with it. Nothing opens by itself: Open appears
 * only for addresses the task allowlist accepts, and a web link names the host it really goes to before the button.
 */
@Composable
fun QrResultCard(found: QrReadResult.Found, actions: QrReadActions, modifier: Modifier = Modifier) {
    val content = found.content
    var askTemplateName by rememberSaveable { mutableStateOf(false) }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.qr_result_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(content.kindRes()), style = MaterialTheme.typography.titleMedium)
            Details(content)
            if (content.openUri != null) {
                Button(onClick = actions.onOpen, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.qr_action_open)) }
            }
            if (content !is QrContent.Unsafe) {
                val label = if (content is QrContent.Wifi) R.string.qr_action_write_wifi else R.string.qr_action_write
                OutlinedButton(onClick = actions.onUse, modifier = Modifier.fillMaxWidth()) { Text(stringResource(label)) }
                OutlinedButton(onClick = { askTemplateName = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.qr_action_template))
                }
            }
            OutlinedButton(onClick = actions.onCopy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.qr_action_copy)) }
            TextButton(onClick = actions.onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    }

    if (askTemplateName) {
        TemplateNameDialog(
            onSave = {
                askTemplateName = false
                actions.onSaveTemplate(it)
            },
            onDismiss = { askTemplateName = false },
        )
    }
}

@Composable
private fun Details(content: QrContent) {
    when (content) {
        is QrContent.Link -> {
            Text(stringResource(R.string.qr_link_goes_to, content.host), style = MaterialTheme.typography.bodyLarge)
            if (!content.secure) Text(stringResource(R.string.qr_link_insecure), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Text(content.raw, style = MaterialTheme.typography.bodySmall, maxLines = LINK_LINES, overflow = TextOverflow.Ellipsis)
        }
        // A Wi-Fi password is shown only in the wizard, never in this preview.
        is QrContent.Wifi -> {
            Text(stringResource(R.string.qr_result_wifi, content.ssid), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(if (content.secured) R.string.qr_wifi_secured else R.string.qr_wifi_open), style = MaterialTheme.typography.bodyMedium)
        }
        is QrContent.Contact -> Text(stringResource(R.string.qr_contact_named, content.name), style = MaterialTheme.typography.bodyLarge)
        is QrContent.Unsafe -> {
            Text(stringResource(R.string.qr_unsafe_body), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Text(content.raw.take(PREVIEW_CHARS), style = MaterialTheme.typography.bodySmall)
        }
        else -> Text(content.raw.take(PREVIEW_CHARS), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun QrContent.kindRes(): Int = when (this) {
    is QrContent.Link -> R.string.qr_kind_link
    is QrContent.Phone -> R.string.qr_kind_phone
    is QrContent.Email -> R.string.qr_kind_email
    is QrContent.Sms -> R.string.qr_kind_sms
    is QrContent.Location -> R.string.qr_kind_location
    is QrContent.Wifi -> R.string.qr_kind_wifi
    is QrContent.Contact -> R.string.qr_kind_contact
    is QrContent.Plain -> R.string.qr_kind_text
    is QrContent.Unsafe -> R.string.qr_kind_unsafe
}

@Composable
private fun TemplateNameDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val suggestion = stringResource(R.string.write_template_default_name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.write_save_template)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.write_template_name)) },
                placeholder = { Text(suggestion) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name.ifBlank { suggestion }) }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
