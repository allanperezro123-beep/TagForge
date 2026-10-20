package com.tagforge.app.feature.productivity

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.domain.location.MapLink
import com.tagforge.domain.location.MapLinkParser
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.WifiAuth
import com.tagforge.domain.ndef.spec.WifiEncryption

/** Everything the create half of the QR screen can ask for. */
data class QrCreateActions(
    val onKind: (QrKind) -> Unit,
    val onSpec: (RecordSpec) -> Unit,
    val onCreate: () -> Unit,
    val onShare: () -> Unit,
    val onSave: () -> Unit,
)

/** Make a QR code from text, a link, a Wi-Fi network, a contact or a place. Nothing here touches a tag. */
@Composable
fun QrCreateSection(state: QrUiState, actions: QrCreateActions, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.qr_create_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.qr_create_intro), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QrKind.entries.forEach { kind ->
                FilterChip(selected = state.create.kind == kind, onClick = { actions.onKind(kind) }, label = { Text(stringResource(kind.labelRes())) })
            }
        }
        when (val spec = state.create.spec) {
            is RecordSpec.Text -> Field(spec.text, R.string.write_field_text, multiline = true) { actions.onSpec(spec.copy(text = it)) }
            is RecordSpec.Uri -> Field(spec.uri, R.string.write_field_url, keyboard = KeyboardType.Uri) { actions.onSpec(spec.copy(uri = it)) }
            is RecordSpec.Wifi -> WifiFields(spec, actions.onSpec)
            is RecordSpec.Contact -> {
                Field(spec.name, R.string.write_field_name) { actions.onSpec(spec.copy(name = it)) }
                Field(spec.phone, R.string.write_field_phone, keyboard = KeyboardType.Phone) { actions.onSpec(spec.copy(phone = it)) }
                Field(spec.email, R.string.write_field_email, keyboard = KeyboardType.Email) { actions.onSpec(spec.copy(email = it)) }
            }
            is RecordSpec.Geo -> LocationFields(spec, actions.onSpec)
            else -> Unit
        }
        Button(onClick = actions.onCreate, enabled = state.canCreate, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.qr_create_button))
        }
    }
}

@Composable
private fun WifiFields(spec: RecordSpec.Wifi, onSpec: (RecordSpec) -> Unit) {
    val secured = spec.auth != WifiAuth.OPEN
    Field(spec.ssid, R.string.write_field_ssid) { onSpec(spec.copy(ssid = it)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.qr_wifi_has_password), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = secured,
            onCheckedChange = { on ->
                onSpec(if (on) spec.copy(auth = WifiAuth.WPA2_PSK, encryption = WifiEncryption.AES) else spec.copy(auth = WifiAuth.OPEN, encryption = WifiEncryption.NONE, password = ""))
            },
        )
    }
    if (secured) Field(spec.password, R.string.write_field_password) { onSpec(spec.copy(password = it)) }
}

/** A place is made by pasting a Maps link or "lat, lon": the same parser the Write wizard uses. */
@Composable
private fun LocationFields(spec: RecordSpec.Geo, onSpec: (RecordSpec) -> Unit) {
    var pasted by rememberSaveable { mutableStateOf("") }
    Field(pasted, R.string.qr_location_paste, keyboard = KeyboardType.Uri) { text ->
        pasted = text
        onSpec((MapLinkParser.parse(text) as? MapLink.Coordinates)?.let { RecordSpec.Geo(it.latitude, it.longitude, it.label) } ?: RecordSpec.Geo())
    }
    val shown = if (spec.latitude.isBlank()) R.string.qr_location_none else R.string.qr_location_found
    Text(
        if (spec.latitude.isBlank()) stringResource(shown) else stringResource(shown, spec.latitude, spec.longitude),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Field(value: String, label: Int, keyboard: KeyboardType = KeyboardType.Text, multiline: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun QrKind.labelRes(): Int = when (this) {
    QrKind.TEXT -> R.string.qr_create_text
    QrKind.URL -> R.string.qr_create_url
    QrKind.WIFI -> R.string.qr_create_wifi
    QrKind.CONTACT -> R.string.qr_create_contact
    QrKind.LOCATION -> R.string.qr_create_location
}
