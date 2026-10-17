package com.tagforge.app.feature.write.forms

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import com.tagforge.app.R
import com.tagforge.domain.ndef.spec.FieldError
import com.tagforge.domain.ndef.spec.SpecError
import com.tagforge.domain.ndef.spec.SpecField

/** The localised explanation of a validation error. The domain only knows codes; this is the one place with text. */
@Composable
fun FieldError.messageText(): String = stringResource(
    when (error) {
        SpecError.REQUIRED -> R.string.write_error_required
        SpecError.INVALID_URL -> R.string.write_error_url
        SpecError.INVALID_EMAIL -> R.string.write_error_email
        SpecError.INVALID_PHONE -> R.string.write_error_phone
        SpecError.INVALID_LANGUAGE -> R.string.write_error_language
        SpecError.NOT_A_NUMBER -> R.string.write_error_number
        SpecError.OUT_OF_RANGE ->
            if (field == SpecField.LATITUDE) R.string.write_error_latitude_range else R.string.write_error_longitude_range
        SpecError.SSID_TOO_LONG -> R.string.write_error_ssid_long
        SpecError.PASSWORD_LENGTH -> R.string.write_error_password_length
        SpecError.INVALID_MAC -> R.string.write_error_mac
        SpecError.TOO_LONG -> R.string.write_error_too_long
        SpecError.INVALID_PACKAGE -> R.string.write_error_package
        SpecError.INVALID_MIME_TYPE -> R.string.write_error_mime
        SpecError.INVALID_HEX -> R.string.write_error_hex
        SpecError.INVALID_EXTERNAL_TYPE -> R.string.write_error_external
    },
)

/**
 * A text field that shows its validation error right beneath itself, but only once the user has left it ([onTouched]
 * fires on the first focus loss) or tried to continue. The error is text, not just a red outline.
 */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    field: SpecField?,
    errors: List<FieldError>,
    onTouched: (SpecField) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val error = field?.let { f -> errors.firstOrNull { it.field == f } }
    var hadFocus by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        isError = error != null,
        supportingText = error?.let { { Text(it.messageText()) } },
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (hadFocus && !state.isFocused && field != null) onTouched(field)
                hadFocus = hadFocus || state.isFocused
            },
    )
}
