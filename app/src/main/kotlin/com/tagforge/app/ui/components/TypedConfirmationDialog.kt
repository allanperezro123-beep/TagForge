package com.tagforge.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.domain.security.SecurityPlan

/**
 * NS-01 as a dialog: the exact commands, what cannot be undone, and a field where the user types the last 4 hex characters
 * of the UID. Confirm stays disabled until [canConfirm]; cancel, back and an outside tap all call [onDismiss], so the
 * safe answer is always "no write".
 */
@Composable
fun TypedConfirmationDialog(
    plan: SecurityPlan,
    typed: String,
    understood: Boolean,
    needsUnderstood: Boolean,
    canConfirm: Boolean,
    onTypedChange: (String) -> Unit,
    onUnderstoodChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.typed_confirm_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.typed_confirm_uid, plan.uid.toHex(":")), style = MaterialTheme.typography.titleSmall)
                if (plan.effects.isNotEmpty()) EffectList(plan.effects)
                Text(stringResource(R.string.typed_confirm_commands), style = MaterialTheme.typography.titleSmall)
                DryRunList(plan.dryRun())
                Text(stringResource(R.string.typed_confirm_prompt), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { onTypedChange(it.take(MAX_TYPED)) },
                    label = { Text(stringResource(R.string.typed_confirm_field), maxLines = 1) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                if (needsUnderstood) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(checked = understood, onCheckedChange = onUnderstoodChange)
                        Text(stringResource(R.string.typed_confirm_understood), Modifier.padding(end = 8.dp))
                    }
                }
                Text(stringResource(R.string.typed_confirm_next), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = canConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.typed_confirm_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private const val MAX_TYPED = 8
