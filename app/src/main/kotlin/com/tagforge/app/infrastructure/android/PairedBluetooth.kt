package com.tagforge.app.infrastructure.android

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.tagforge.domain.ndef.spec.MacAddressInput

/** A device the phone is already paired with: what the person recognises ([name]) and what the record stores ([address]). */
data class PairedDevice(val name: String, val address: String)

sealed interface PairedDevices {
    data class Found(val devices: List<PairedDevice>) : PairedDevices

    /** Bluetooth is on but nothing is paired yet. */
    data object Empty : PairedDevices
    data object BluetoothOff : PairedDevices

    /** The phone has no Bluetooth adapter. */
    data object NoBluetooth : PairedDevices
    data object NoPermission : PairedDevices
}

/** The list of paired devices, so nobody has to find and type a Bluetooth address. Read once per tap; no scanning, no connecting. */
class PairedBluetooth(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun load(): PairedDevices {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return PairedDevices.NoBluetooth
        if (needsRuntimePermission && !hasPermission(context)) return PairedDevices.NoPermission
        return try {
            if (!adapter.isEnabled) return PairedDevices.BluetoothOff
            val devices = adapter.bondedDevices.orEmpty().mapNotNull { device ->
                val address = MacAddressInput.parse(device.address) ?: return@mapNotNull null
                PairedDevice(device.name?.takeIf { it.isNotBlank() } ?: address, address)
            }.sortedBy { it.name.lowercase() }
            if (devices.isEmpty()) PairedDevices.Empty else PairedDevices.Found(devices)
        } catch (_: SecurityException) {
            PairedDevices.NoPermission
        }
    }

    companion object {
        /** From Android 12 the list needs BLUETOOTH_CONNECT at run time; before it, the install-time BLUETOOTH permission is enough. */
        val needsRuntimePermission: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }
}
