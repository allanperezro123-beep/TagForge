package com.tagforge.app.feature.write.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.domain.ndef.spec.FieldError
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.SpecField

private val FormSpacing = Arrangement.spacedBy(8.dp)

@Composable
fun TextForm(
    spec: RecordSpec.Text,
    errors: List<FieldError>,
    onChange: (RecordSpec) -> Unit,
    onTouched: (SpecField) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = FormSpacing) {
        FormTextField(
            value = spec.text,
            onValueChange = { onChange(spec.copy(text = it)) },
            label = stringResource(R.string.write_field_text),
            field = SpecField.TEXT,
            errors = errors,
            onTouched = onTouched,
            singleLine = false,
            minLines = 3,
        )
        LanguageField(
            code = spec.languageCode,
            onPick = {
                onChange(spec.copy(languageCode = it))
                onTouched(SpecField.LANGUAGE)
            },
            errorText = errors.firstOrNull { it.field == SpecField.LANGUAGE }?.messageText(),
        )
    }
}

@Composable
fun UriForm(
    spec: RecordSpec.Uri,
    errors: List<FieldError>,
    onChange: (RecordSpec) -> Unit,
    onTouched: (SpecField) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = FormSpacing) {
        FormTextField(
            value = spec.uri,
            onValueChange = { onChange(spec.copy(uri = it)) },
            label = stringResource(R.string.write_field_url),
            field = SpecField.URI,
            errors = errors,
            onTouched = onTouched,
            placeholder = stringResource(R.string.write_hint_url),
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
fun EmailForm(
    spec: RecordSpec.Email,
    errors: List<FieldError>,
    onChange: (RecordSpec) -> Unit,
    onTouched: (SpecField) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = FormSpacing) {
        FormTextField(
            value = spec.to,
            onValueChange = { onChange(spec.copy(to = it)) },
            label = stringResource(R.string.write_field_to),
            field = SpecField.TO,
            errors = errors,
            onTouched = onTouched,
            keyboardType = KeyboardType.Email,
        )
        FormTextField(
            value = spec.subject,
            onValueChange = { onChange(spec.copy(subject = it)) },
            label = stringResource(R.string.write_field_subject),
            field = null,
            errors = errors,
            onTouched = onTouched,
        )
        FormTextField(
            value = spec.body,
            onValueChange = { onChange(spec.copy(body = it)) },
            label = stringResource(R.string.write_field_body),
            field = null,
            errors = errors,
            onTouched = onTouched,
            singleLine = false,
            minLines = 3,
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
fun PhoneForm(
    spec: RecordSpec.Phone,
    errors: List<FieldError>,
    onChange: (RecordSpec) -> Unit,
    onTouched: (SpecField) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = FormSpacing) {
        FormTextField(
            value = spec.number,
            onValueChange = { onChange(spec.copy(number = it)) },
            label = stringResource(R.string.write_field_number),
            field = SpecField.NUMBER,
            errors = errors,
            onTouched = onTouched,
            placeholder = stringResource(R.string.write_hint_phone),
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
fun SmsForm(
    spec: RecordSpec.Sms,
    errors: List<FieldError>,
    onChange: (RecordSpec) -> Unit,
    onTouched: (SpecField) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = FormSpacing) {
        FormTextField(
            value = spec.number,
            onValueChange = { onChange(spec.copy(number = it)) },
            label = stringResource(R.string.write_field_number),
            field = SpecField.NUMBER,
            errors = errors,
            onTouched = onTouched,
            placeholder = stringResource(R.string.write_hint_phone),
            keyboardType = KeyboardType.Phone,
        )
        FormTextField(
            value = spec.body,
            onValueChange = { onChange(spec.copy(body = it)) },
            label = stringResource(R.string.write_field_body),
            field = null,
            errors = errors,
            onTouched = onTouched,
            singleLine = false,
            minLines = 3,
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
fun GeoForm(
    spec: RecordSpec.Geo,
    errors: List<FieldError>,
    onChange: (RecordSpec) -> Unit,
    onTouched: (SpecField) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = FormSpacing) {
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
