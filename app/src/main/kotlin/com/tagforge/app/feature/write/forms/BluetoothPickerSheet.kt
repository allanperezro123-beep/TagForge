package com.tagforge.app.feature.write.forms

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.infrastructure.android.PairedBluetooth
import com.tagforge.app.infrastructure.android.PairedDevice
import com.tagforge.app.infrastructure.android.PairedDevices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The phone's paired Bluetooth devices, by name with the address underneath. Every other state (Bluetooth off, nothing paired,
 * permission refused, no Bluetooth at all) says what is wrong and offers the one button that fixes it.
 * [reloadKey] changes when the permission answer arrives, so the list is read again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothPickerSheet(reloadKey: Int, onRequestPermission: () -> Unit, onPick: (PairedDevice) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var devices by remember(reloadKey) { mutableStateOf<PairedDevices?>(null) }
    LaunchedEffect(reloadKey) { devices = withContext(Dispatchers.IO) { PairedBluetooth(context.applicationContext).load() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.bt_sheet_title), style = MaterialTheme.typography.titleLarge)
            when (val state = devices) {
                null -> Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.bt_sheet_loading), Modifier.padding(start = 16.dp))
                }
                is PairedDevices.Found -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(state.devices, key = { it.address }) { device -> DeviceRow(device) { onPick(device) } }
                }
                PairedDevices.Empty -> Problem(R.string.bt_sheet_empty) { SettingsButton(Settings.ACTION_BLUETOOTH_SETTINGS, R.string.bt_open_settings) }
                PairedDevices.BluetoothOff -> Problem(R.string.bt_sheet_off) { SettingsButton(Settings.ACTION_BLUETOOTH_SETTINGS, R.string.bt_open_settings) }
                PairedDevices.NoBluetooth -> Problem(R.string.bt_sheet_none) {}
                PairedDevices.NoPermission -> Problem(R.string.bt_sheet_denied) {
                    Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bt_sheet_allow)) }
                    SettingsButton(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, R.string.loc_open_app_settings, withPackage = true)
                }
            }
            Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun DeviceRow(device: PairedDevice, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp)) {
        Text(device.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Problem(message: Int, actions: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
        actions()
    }
}

@Composable
private fun SettingsButton(action: String, label: Int, withPackage: Boolean = false) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val intent = Intent(action).apply { if (withPackage) data = Uri.fromParts("package", context.packageName, null) }
            runCatching { context.startActivity(intent) }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(label)) }
}
