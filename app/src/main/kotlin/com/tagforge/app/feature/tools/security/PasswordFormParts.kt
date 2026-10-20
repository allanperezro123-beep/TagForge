package com.tagforge.app.feature.tools.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.domain.security.SecretKind

private const val MIN_AUTH0 = 4
private const val MAX_AUTH0 = 229

@Composable
fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, labelRes: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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

/** The new password with its generator and the copy/share tools (NS-02). PACK and AUTH0 live under the advanced options. */
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
        SecretField(
            form.password, { v -> onChange { it.copy(password = v, storedAck = false) } },
            R.string.security_form_password, form.mode, SecretKind.PASSWORD,
            isError = problem == FormProblem.PASSWORD_INVALID, startVisible = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onGenerate, modifier = Modifier.weight(1f)) { ButtonLabel(R.string.security_form_generate) }
            OutlinedButton(onClick = { onCopy(form.password) }, enabled = form.password.isNotEmpty(), modifier = Modifier.weight(1f)) {
                ButtonLabel(R.string.security_form_copy)
            }
        }
        OutlinedButton(onClick = { onShare(form.password) }, enabled = form.password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            ButtonLabel(R.string.security_form_share)
        }
        Text(stringResource(R.string.security_form_shown_once), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CheckRow(form.storedAck, { v -> onChange { it.copy(storedAck = v) } }, R.string.security_form_stored)
    }
}

@Composable
private fun ButtonLabel(res: Int) {
    Text(stringResource(res), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
