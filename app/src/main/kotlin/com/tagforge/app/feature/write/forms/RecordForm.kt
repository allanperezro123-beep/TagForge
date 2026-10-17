package com.tagforge.app.feature.write.forms

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tagforge.domain.ndef.spec.FieldError
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.SpecField

/** Picks the form for [spec]. Each form edits its record through [onChange] and reports focus loss through [onTouched]. */
@Composable
fun RecordForm(
    spec: RecordSpec,
    errors: List<FieldError>,
    onChange: (RecordSpec) -> Unit,
    onTouched: (SpecField) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (spec) {
        is RecordSpec.Text -> TextForm(spec, errors, onChange, onTouched, modifier)
        is RecordSpec.Uri -> UriForm(spec, errors, onChange, onTouched, modifier)
        is RecordSpec.Email -> EmailForm(spec, errors, onChange, onTouched, modifier)
        is RecordSpec.Phone -> PhoneForm(spec, errors, onChange, onTouched, modifier)
        is RecordSpec.Sms -> SmsForm(spec, errors, onChange, onTouched, modifier)
        is RecordSpec.Geo -> GeoForm(spec, errors, onChange, onTouched, modifier)
        is RecordSpec.Contact -> ContactForm(spec, errors, onChange, onTouched, modifier)
        is RecordSpec.Wifi -> WifiForm(spec, errors, onChange, onTouched, modifier)
        is RecordSpec.Bluetooth -> BluetoothForm(spec, errors, onChange, onTouched, modifier)
        is RecordSpec.AppLaunch -> AppLaunchForm(spec, errors, onChange, onTouched, modifier)
        is RecordSpec.Mime -> MimeForm(spec, errors, onChange, onTouched, modifier)
        is RecordSpec.External -> ExternalForm(spec, errors, onChange, onTouched, modifier)
    }
}
