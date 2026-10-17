package com.tagforge.app.feature.write.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.text.labelRes
import com.tagforge.domain.ndef.spec.FieldError
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.SpecField
import com.tagforge.domain.ndef.spec.WifiAuth
import com.tagforge.domain.ndef.spec.WifiEncryption

private val FormSpacing = Arrangement.spacedBy(8.dp)

@Composable
fun ContactForm(
    spec: RecordSpec.Contact,
    errors: List<FieldError>,
    onChange: (RecordSpec) -> Unit,
    onTouched: (SpecField) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = FormSpacing) {
        FormTextField(
            value = spec.name,
            onValueChange = { onChange(spec.copy(name = it)) },
            label = stringResource(R.string.write_field_name),
            field = SpecField.NAME,
            errors = errors,
            onTouched = onTouched,
        )
        FormTextField(
            value = spec.phone,
            onValueChange = { onChange(spec.copy(phone = it)) },
            label = stringResource(R.string.write_field_phone),
            field = SpecField.PHONE,
            errors = errors,
            onTouched = onTouched,
            placeholder = stringResource(R.string.write_hint_phone),
            keyboardType = KeyboardType.Phone,
        )
        FormTextField(
            value = spec.email,
            onValueChange = { onChange(spec.copy(email = it)) },
            label = stringResource(R.string.write_field_email),
            field = SpecField.EMAIL,
            errors = errors,
            onTouched = onTouched,
            keyboardType = KeyboardType.Email,
        )
        FormTextField(
            value = spec.org,
            onValueChange = { onChange(spec.copy(org = it)) },
            label = stringResource(R.string.write_field_org),
            field = null,
            errors = errors,
            onTouched = onTouched,
        )
        FormTextField(
            value = spec.url,
            onValueChange = { onChange(spec.copy(url = it)) },
            label = stringResource(R.string.write_field_site),
            field = SpecField.URL,
            errors = errors,
            onTouched = onTouched,
            placeholder = stringResource(R.string.write_hint_url),
            keyboardType = KeyboardType.Uri,
        )
        FormTextField(
            value = spec.note,
            onValueChange = { onChange(spec.copy(note = it)) },
            label = stringResource(R.string.write_field_note),
            field = null,
            errors = errors,
            onTouched = onTouched,
            singleLine = false,
            minLines = 2,
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
fun WifiForm(
    spec: RecordSpec.Wifi,
    errors: List<FieldError>,
    onChange: (RecordSpec) -> Unit,
    onTouched: (SpecField) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val secured = spec.auth != WifiAuth.OPEN

    Column(modifier, verticalArrangement = FormSpacing) {
        FormTextField(
            value = spec.ssid,
            onValueChange = { onChange(spec.copy(ssid = it)) },
            label = stringResource(R.string.write_field_ssid),
            field = SpecField.SSID,
            errors = errors,
            onTouched = onTouched,
        )

        ChipGroup(label = stringResource(R.string.write_field_security)) {
            WifiAuth.entries.forEach { auth ->
                FilterChip(
                    selected = spec.auth == auth,
                    onClick = {
                        // Open networks carry no encryption; switching back to a secured mode restores a sensible default.
                        val encryption = when {
                            auth == WifiAuth.OPEN -> WifiEncryption.NONE
                            spec.encryption == WifiEncryption.NONE -> WifiEncryption.AES
                            else -> spec.encryption
                        }
                        onChange(spec.copy(auth = auth, encryption = encryption))
                    },
                    label = { Text(stringResource(auth.labelRes())) },
                )
            }
        }

        if (secured) {
            ChipGroup(label = stringResource(R.string.write_field_encryption)) {
                listOf(WifiEncryption.AES, WifiEncryption.TKIP).forEach { encryption ->
                    FilterChip(
                        selected = spec.encryption == encryption,
                        onClick = { onChange(spec.copy(encryption = encryption)) },
                        label = { Text(stringResource(encryption.labelRes())) },
                    )
                }
            }
            FormTextField(
                value = spec.password,
                onValueChange = { onChange(spec.copy(password = it)) },
                label = stringResource(R.string.write_field_password),
                field = SpecField.PASSWORD,
                errors = errors,
                onTouched = onTouched,
                imeAction = ImeAction.Done,
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = stringResource(if (showPassword) R.string.write_password_hide else R.string.write_password_show),
                        )
                    }
                },
            )
        } else {
            Text(
                stringResource(R.string.write_wifi_open_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun BluetoothForm(
    spec: RecordSpec.Bluetooth,
    errors: List<FieldError>,
    onChange: (RecordSpec) -> Unit,
    onTouched: (SpecField) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = FormSpacing) {
        FormTextField(
            value = spec.mac,
            onValueChange = { onChange(spec.copy(mac = it)) },
            label = stringResource(R.string.write_field_mac),
            field = SpecField.MAC,
            errors = errors,
            onTouched = onTouched,
            placeholder = stringResource(R.string.write_hint_mac),
        )
        FormTextField(
            value = spec.name,
            onValueChange = { onChange(spec.copy(name = it)) },
            label = stringResource(R.string.write_field_device),
            field = SpecField.DEVICE_NAME,
            errors = errors,
            onTouched = onTouched,
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
private fun ChipGroup(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}
