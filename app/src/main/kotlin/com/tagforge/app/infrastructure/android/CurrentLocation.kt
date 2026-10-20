package com.tagforge.app.infrastructure.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.tagforge.domain.location.CoordinateText
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed interface LocationFix {
    data class Got(val latitude: String, val longitude: String) : LocationFix

    /** Neither location permission is granted. */
    data object NoPermission : LocationFix

    /** Location is switched off in the phone's settings. */
    data object ServicesOff : LocationFix

    /** Location is on but no position arrived in time. */
    data object NoFix : LocationFix
}

/**
 * One position, once, while the user is looking at the screen: no updates, no background use, no Google Play Services. Android 11+
 * asks for a fresh fix; older versions use the newest position the system already knows.
 */
class CurrentLocation(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun get(): LocationFix {
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!fine && !coarse) return LocationFix.NoPermission
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return LocationFix.ServicesOff
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !manager.isLocationEnabled) return LocationFix.ServicesOff

        val providers = buildList {
            if (fine) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return LocationFix.ServicesOff

        return try {
            val location = freshFix(manager, providers) ?: lastKnown(manager, providers)
            location?.let { LocationFix.Got(CoordinateText.format(it.latitude), CoordinateText.format(it.longitude)) } ?: LocationFix.NoFix
        } catch (_: SecurityException) {
            LocationFix.NoPermission
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshFix(manager: LocationManager, providers: List<String>): Location? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        for (provider in providers) {
            val location = withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine<Location?> { continuation ->
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { continuation.resume(it) }
                }
            }
            if (location != null) return location
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager, providers: List<String>): Location? =
        providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val FIX_TIMEOUT_MILLIS = 15_000L
    }
}
