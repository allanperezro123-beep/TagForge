package com.tagforge.app.feature.write.forms

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.infrastructure.android.PairedBluetooth
import com.tagforge.domain.ndef.spec.FieldError
import com.tagforge.domain.ndef.spec.MacAddressInput
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.SpecField

/**
 * A Bluetooth device record. The easy way is to pick one the phone is already paired with (the permission to read that list is
 * asked only when the button is tapped). Typing the address still works, formatted as it is typed, with a hint on where to find it.
 */
@Composable
fun BluetoothForm(
    spec: RecordSpec.Bluetooth,
    errors: List<FieldError>,
    onChange: (RecordSpec) -> Unit,
    onTouched: (SpecField) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        reloadKey++
        showSheet = true
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                if (PairedBluetooth.needsRuntimePermission && !PairedBluetooth.hasPermission(context)) {
                    permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    showSheet = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.bt_pick_button)) }
        Text(stringResource(R.string.bt_pick_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text(stringResource(R.string.bt_manual_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        FormTextField(
            value = spec.mac,
            onValueChange = { onChange(spec.copy(mac = MacAddressInput.format(it))) },
            label = stringResource(R.string.write_field_mac),
            field = SpecField.MAC,
            errors = errors,
            onTouched = onTouched,
            placeholder = stringResource(R.string.write_hint_mac),
        )
        Text(stringResource(R.string.bt_where), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    if (showSheet) {
        BluetoothPickerSheet(
            reloadKey = reloadKey,
            onRequestPermission = { permission.launch(Manifest.permission.BLUETOOTH_CONNECT) },
            onPick = {
                showSheet = false
                onChange(spec.copy(mac = it.address, name = it.name))
                onTouched(SpecField.MAC)
            },
            onDismiss = { showSheet = false },
        )
    }
}
