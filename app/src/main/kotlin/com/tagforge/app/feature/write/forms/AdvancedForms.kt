package com.tagforge.app.feature.write.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.domain.ndef.spec.FieldError
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.SpecField

private val FormSpacing = Arrangement.spacedBy(8.dp)

@Composable
fun AppLaunchForm(
    spec: RecordSpec.AppLaunch,
    errors: List<FieldError>,
    onChange: (RecordSpec) -> Unit,
    onTouched: (SpecField) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = FormSpacing) {
        FormTextField(
            value = spec.packageName,
            onValueChange = { onChange(spec.copy(packageName = it)) },
            label = stringResource(R.string.write_field_package),
            field = SpecField.PACKAGE,
            errors = errors,
            onTouched = onTouched,
            placeholder = stringResource(R.string.write_hint_package),
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
fun MimeForm(
    spec: RecordSpec.Mime,
    errors: List<FieldError>,
    onChange: (RecordSpec) -> Unit,
    onTouched: (SpecField) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = FormSpacing) {
        FormTextField(
            value = spec.mimeType,
            onValueChange = { onChange(spec.copy(mimeType = it)) },
            label = stringResource(R.string.write_field_mime),
            field = SpecField.MIME_TYPE,
            errors = errors,
            onTouched = onTouched,
            placeholder = stringResource(R.string.write_hint_mime),
        )
        FormTextField(
            value = spec.payloadText,
            onValueChange = { onChange(spec.copy(payloadText = it)) },
            label = stringResource(R.string.write_field_payload_text),
            field = SpecField.PAYLOAD,
            errors = errors,
            onTouched = onTouched,
            singleLine = false,
            minLines = 3,
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
fun ExternalForm(
    spec: RecordSpec.External,
    errors: List<FieldError>,
    onChange: (RecordSpec) -> Unit,
    onTouched: (SpecField) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = FormSpacing) {
        FormTextField(
            value = spec.domain,
            onValueChange = { onChange(spec.copy(domain = it)) },
            label = stringResource(R.string.write_field_domain),
            field = SpecField.DOMAIN,
            errors = errors,
            onTouched = onTouched,
            placeholder = stringResource(R.string.write_hint_domain),
        )
        FormTextField(
            value = spec.type,
            onValueChange = { onChange(spec.copy(type = it)) },
            label = stringResource(R.string.write_field_ext_type),
            field = SpecField.TYPE,
            errors = errors,
            onTouched = onTouched,
        )
        FormTextField(
            value = spec.payloadHex,
            onValueChange = { onChange(spec.copy(payloadHex = it)) },
            label = stringResource(R.string.write_field_payload_hex),
            field = SpecField.PAYLOAD,
            errors = errors,
            onTouched = onTouched,
            imeAction = ImeAction.Done,
        )
    }
}
