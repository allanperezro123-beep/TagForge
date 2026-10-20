package com.tagforge.app.feature.write.forms

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.domain.ndef.spec.FieldError
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.SpecField

/**
 * The Location record, simplest way first: search an address, use where the phone is now, pick the place in Google Maps or any
 * other maps app, or paste a Maps link. Typing latitude and longitude is still possible, but folded under "Advanced".
 */
@Composable
fun GeoForm(
    spec: RecordSpec.Geo,
    errors: List<FieldError>,
    onChange: (RecordSpec) -> Unit,
    onTouched: (SpecField) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    val chosen = spec.latitude.isNotBlank() && spec.longitude.isNotBlank()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (chosen) ChosenPlace(spec)
        LocationSearchSection(onPick = { onChange(RecordSpec.Geo(it.latitude, it.longitude, it.label)) })
        CurrentLocationSection(onFound = { lat, lon -> onChange(RecordSpec.Geo(lat, lon, spec.query)) })
        MapsAppSection()
        PasteMapLinkSection(
            onCoordinates = { onChange(RecordSpec.Geo(it.latitude, it.longitude, it.label)) },
            // No coordinates (a short link): keep the address itself; it opens Maps when the tag is scanned.
            onWebLink = { onChange(RecordSpec.Uri(it.url)) },
        )
        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(stringResource(if (showAdvanced) R.string.loc_advanced_hide else R.string.loc_advanced))
        }
        if (showAdvanced || errors.any { it.field == SpecField.LATITUDE || it.field == SpecField.LONGITUDE }) {
            FormTextField(
                value = spec.latitude,
                onValueChange = { onChange(spec.copy(latitude = it)) },
                label = stringResource(R.string.write_field_latitude),
                field = SpecField.LATITUDE,
                errors = errors,
                onTouched = onTouched,
                placeholder = stringResource(R.string.write_hint_latitude),
                keyboardType = KeyboardType.Decimal,
            )
            FormTextField(
                value = spec.longitude,
                onValueChange = { onChange(spec.copy(longitude = it)) },
                label = stringResource(R.string.write_field_longitude),
                field = SpecField.LONGITUDE,
                errors = errors,
                onTouched = onTouched,
                placeholder = stringResource(R.string.write_hint_longitude),
                keyboardType = KeyboardType.Decimal,
            )
            FormTextField(
                value = spec.query,
                onValueChange = { onChange(spec.copy(query = it)) },
                label = stringResource(R.string.write_field_place),
                field = null,
                errors = errors,
                onTouched = onTouched,
                imeAction = ImeAction.Done,
            )
        }
    }
}

@Composable
private fun ChosenPlace(spec: RecordSpec.Geo) {
    val context = LocalContext.current
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.loc_chosen_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (spec.query.isNotBlank()) Text(spec.query, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("${spec.latitude}, ${spec.longitude}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = {
                val view = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${spec.latitude},${spec.longitude}"))
                try {
                    context.startActivity(view)
                } catch (_: ActivityNotFoundException) {
                    // No maps app installed: the coordinates above are still right, so there is nothing else to do.
                }
            }) { Text(stringResource(R.string.loc_view_on_map)) }
        }
    }
}
