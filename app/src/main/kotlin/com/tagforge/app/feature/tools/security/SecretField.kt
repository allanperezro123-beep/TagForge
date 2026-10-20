package com.tagforge.app.feature.tools.security

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.tagforge.app.R
import com.tagforge.domain.common.errorOrNull
import com.tagforge.domain.security.PasswordInput
import com.tagforge.domain.security.PasswordInputError
import com.tagforge.domain.security.PasswordMode
import com.tagforge.domain.security.SecretKind

/**
 * One secret (password or PACK) typed as plain text by default. It shows a live counter ("3/4 characters"), explains in
 * plain language what is wrong as soon as it is certain, and keeps the whole value visible at large font sizes.
 */
@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    mode: PasswordMode,
    kind: SecretKind,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    startVisible: Boolean = false,
) {
    var visible by remember { mutableStateOf(startVisible) }
    val error = if (kind == SecretKind.PASSWORD) PasswordInput.password(mode, value).errorOrNull() else PasswordInput.parse(mode, kind, value).errorOrNull()
    val problem = error?.takeIf { it.isDefinitive() }
    val counter = stringResource(
        if (mode == PasswordMode.TEXT) R.string.security_pw_count_text else R.string.security_pw_count_hex,
        PasswordInput.count(mode, value), PasswordInput.capacity(mode, kind),
    )
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(PasswordInput.sanitize(mode, kind, it)) },
            label = { Text(stringResource(labelRes), maxLines = 1) },
            supportingText = { Text(problem?.let { stringResource(it.messageRes()) } ?: counter) },
            singleLine = true,
            enabled = enabled,
            isError = isError || problem != null,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            visualTransformation = when {
                !visible -> PasswordVisualTransformation()
                mode == PasswordMode.HEX -> HexGroupTransformation
                else -> VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(
                capitalization = if (mode == PasswordMode.HEX) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
            ),
        )
        TextButton(onClick = { visible = !visible }) {
            Text(stringResource(if (visible) R.string.security_form_hide else R.string.security_form_show), maxLines = 1)
        }
    }
}

/** Errors that are certain while typing; "too short" is just the counter, not a scolding. */
private fun PasswordInputError.isDefinitive(): Boolean = when (this) {
    PasswordInputError.Empty, is PasswordInputError.WrongLength -> false
    else -> true
}

private fun PasswordInputError.messageRes(): Int = when (this) {
    PasswordInputError.NonAscii -> R.string.security_pw_err_nonascii
    PasswordInputError.HasSpace -> R.string.security_pw_err_space
    PasswordInputError.NotHex -> R.string.security_pw_err_nothex
    PasswordInputError.FactoryValue -> R.string.security_pw_err_factory
    else -> R.string.security_pw_err_nonascii
}

/** "12345678" is shown as "12 34 56 78"; the stored value never contains the spaces, so backspace behaves. */
private object HexGroupTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val grouped = PasswordInput.groupHex(digits)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = if (offset <= 0) 0 else offset + (offset - 1) / 2
            override fun transformedToOriginal(offset: Int): Int = (offset - offset / 3).coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(grouped), mapping)
    }
}
