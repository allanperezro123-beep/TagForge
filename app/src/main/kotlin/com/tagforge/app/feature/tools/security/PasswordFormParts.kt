package com.tagforge.app.feature.tools.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tagforge.app.R

private const val MAX_HEX_PASSWORD = 8
private const val MAX_HEX_PACK = 4
private const val MIN_AUTH0 = 4
private const val MAX_AUTH0 = 229

/** A hex field that hides its content on request. The password is shown by default here: the user has to save it. */
@Composable
fun HexSecretField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    maxChars: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    startVisible: Boolean = false,
) {
    var visible by remember { mutableStateOf(startVisible) }
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.filter(Char::isLetterOrDigit).uppercase().take(maxChars)) },
            label = { Text(stringResource(labelRes)) },
            singleLine = true,
            enabled = enabled,
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
            ),
        )
        TextButton(onClick = { visible = !visible }) {
            Text(stringResource(if (visible) R.string.security_form_hide else R.string.security_form_show))
        }
    }
}

@Composable
fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, labelRes: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SwitchRow(checked: Boolean, onChange: (Boolean) -> Unit, labelRes: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Minus and plus around a number, for AUTH0 and the attempt limit. */
@Composable
fun StepperRow(label: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onMinus) { Text("−") }
        OutlinedButton(onClick = onPlus) { Text("+") }
    }
}

/** Password, PACK, generator and the copy/share tools for NS-02. */
@Composable
fun PasswordFields(
    form: SecurityForm,
    problem: FormProblem?,
    onChange: ((SecurityForm) -> SecurityForm) -> Unit,
    onGenerate: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HexSecretField(
            form.passwordHex, { v -> onChange { it.copy(passwordHex = v, storedAck = false) } },
            R.string.security_form_password, MAX_HEX_PASSWORD, isError = problem == FormProblem.PASSWORD_INVALID, startVisible = true,
        )
        HexSecretField(
            form.packHex, { v -> onChange { it.copy(packHex = v) } },
            R.string.security_form_pack, MAX_HEX_PACK, isError = problem == FormProblem.PACK_INVALID, startVisible = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onGenerate) { Text(stringResource(R.string.security_form_generate)) }
            OutlinedButton(onClick = { onCopy(form.passwordHex) }, enabled = form.passwordHex.isNotEmpty()) { Text(stringResource(R.string.security_form_copy)) }
            OutlinedButton(onClick = { onShare(form.passwordHex) }, enabled = form.passwordHex.isNotEmpty()) { Text(stringResource(R.string.security_form_share)) }
        }
        Text(stringResource(R.string.security_form_shown_once), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CheckRow(form.storedAck, { v -> onChange { it.copy(storedAck = v) } }, R.string.security_form_stored)
    }
}

@Composable
fun Auth0Stepper(auth0: Int, onChange: (Int) -> Unit) {
    Column {
        StepperRow(
            stringResource(R.string.security_form_auth0, auth0),
            onMinus = { onChange((auth0 - 1).coerceAtLeast(MIN_AUTH0)) },
            onPlus = { onChange((auth0 + 1).coerceAtMost(MAX_AUTH0)) },
        )
        Text(stringResource(R.string.security_form_auth0_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
