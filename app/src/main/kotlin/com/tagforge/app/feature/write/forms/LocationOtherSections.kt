package com.tagforge.app.feature.write.forms

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tagforge.app.R
import com.tagforge.app.infrastructure.android.CurrentLocation
import com.tagforge.app.infrastructure.android.LocationFix
import com.tagforge.domain.location.MapLink
import com.tagforge.domain.location.MapLinkParser
import kotlinx.coroutines.launch

/** "Use my current location": the permission is asked when the button is tapped, one position is read, nothing keeps listening. */
@Composable
fun CurrentLocationSection(onFound: (latitude: String, longitude: String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source = remember { CurrentLocation(context.applicationContext) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<Int?>(null) }

    fun locate() {
        busy = true
        message = null
        scope.launch {
            when (val fix = source.get()) {
                is LocationFix.Got -> onFound(fix.latitude, fix.longitude)
                LocationFix.ServicesOff -> message = R.string.loc_here_off
                LocationFix.NoFix -> message = R.string.loc_here_nofix
                LocationFix.NoPermission -> message = R.string.loc_here_denied
            }
            busy = false
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) locate() else message = R.string.loc_here_denied
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                val allowed = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                if (allowed) locate() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.loc_here_button)) }
        if (busy) CircularProgressIndicator()
        message?.let { res ->
            Text(stringResource(res), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val settings = if (res == R.string.loc_here_off) Settings.ACTION_LOCATION_SOURCE_SETTINGS else Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            TextButton(onClick = {
                val uri = if (res == R.string.loc_here_off) null else Uri.fromParts("package", context.packageName, null)
                runCatching { context.startActivity(Intent(settings, uri)) }
            }) { Text(stringResource(if (res == R.string.loc_here_off) R.string.loc_open_location_settings else R.string.loc_open_app_settings)) }
        }
    }
}

/** "Choose in Google Maps or another app": opens whatever maps app the person likes; they come back with Share > Tag Forge. */
@Composable
fun MapsAppSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var missing by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = {
                val maps = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
                try {
                    context.startActivity(Intent.createChooser(maps, context.getString(R.string.loc_maps_chooser)))
                } catch (_: ActivityNotFoundException) {
                    missing = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.loc_maps_button)) }
        Text(
            stringResource(if (missing) R.string.loc_maps_missing else R.string.loc_maps_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Paste a Maps link": coordinates are read out of the link; a short link is kept as a web link that opens Maps when scanned. */
@Composable
fun PasteMapLinkSection(onCoordinates: (MapLink.Coordinates) -> Unit, onWebLink: (MapLink.Web) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    var unrecognized by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                unrecognized = false
            },
            label = { Text(stringResource(R.string.loc_paste_label)) },
            isError = unrecognized,
            supportingText = { Text(stringResource(if (unrecognized) R.string.loc_paste_unrecognized else R.string.loc_paste_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                when (val link = MapLinkParser.parse(text)) {
                    is MapLink.Coordinates -> onCoordinates(link)
                    is MapLink.Web -> onWebLink(link)
                    MapLink.Unrecognized -> unrecognized = true
                }
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.loc_paste_button)) }
    }
}
