package com.tagforge.app.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.tagforge.app.R

/** Keyboard for a secret (S6): password layout, no autocorrect, so a key is never learned by a suggestion engine. */
fun secretKeyboardOptions(imeAction: ImeAction = ImeAction.Default): KeyboardOptions =
    KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = imeAction)

/** Dots while hidden, the text while the user chose to look at it. */
fun secretTransformation(visible: Boolean): VisualTransformation =
    if (visible) VisualTransformation.None else PasswordVisualTransformation()

/** The eye button of a masked field. Reuses the Write wizard's labels so the screen reader says the same thing everywhere. */
@Composable
fun SecretVisibilityToggle(visible: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = stringResource(if (visible) R.string.write_password_hide else R.string.write_password_show),
        )
    }
}
