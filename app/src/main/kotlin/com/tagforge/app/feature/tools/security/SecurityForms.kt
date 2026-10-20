package com.tagforge.app.feature.tools.security

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.text.SecurityErrorText
import com.tagforge.app.ui.text.messageText
import com.tagforge.domain.security.LockScope
import com.tagforge.domain.security.PasswordMode
import com.tagforge.domain.security.SecretKind
import com.tagforge.domain.security.SecurityFailure

/** Everything the forms can do, as lambdas so the screen renders without a ViewModel (AS-05). */
data class SecurityFormActions(
    val onChange: ((SecurityForm) -> SecurityForm) -> Unit,
    val onGenerate: () -> Unit,
    val onLimitUnderstood: () -> Unit,
    val onProbeLimit: () -> Unit,
    val onNext: () -> Unit,
    val onCopy: (String) -> Unit,
    val onShare: (String) -> Unit,
    val onModeChange: (PasswordMode) -> Unit,
)

@Composable
fun SecurityFormContent(state: SecurityUiState, actions: SecurityFormActions, modifier: Modifier = Modifier) {
    val action = state.action ?: return
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (action) {
            SecurityAction.SET_PASSWORD -> SetPasswordFields(state, actions)
            SecurityAction.REMOVE_PASSWORD -> {
                CurrentPasswordField(state, actions, R.string.security_form_current_required)
                PasswordAdvancedSection(state.form.mode, actions.onModeChange)
            }
            SecurityAction.UNLOCK -> UnlockFields(state, actions)
            SecurityAction.CONFIG_LOCK -> ConfigLockFields(state, actions)
            SecurityAction.LOCK -> LockFields(state, actions)
        }
        FormProblemText(state.problem, state.failure, state.form.mode)
        Text(
            stringResource(R.string.security_form_next_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = actions.onNext,
            enabled = state.canStart && state.canGoNext,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (action == SecurityAction.UNLOCK) R.string.security_unlock_action else R.string.security_form_next)) }
    }
}

@Composable
private fun SetPasswordFields(state: SecurityUiState, actions: SecurityFormActions) {
    PasswordFields(state.form, state.problem, actions.onChange, actions.onGenerate, actions.onCopy, actions.onShare)
    SwitchRow(state.form.protectReads, { v -> actions.onChange { it.copy(protectReads = v) } }, R.string.security_form_protect_reads)
    CurrentPasswordField(state, actions, R.string.security_form_current)
    PasswordAdvancedSection(state.form.mode, actions.onModeChange) {
        PackField(state.form, state.problem, actions.onChange)
        Auth0Stepper(state.form.auth0) { value -> actions.onChange { it.copy(auth0 = value) } }
    }
}

@Composable
private fun CurrentPasswordField(state: SecurityUiState, actions: SecurityFormActions, @StringRes label: Int) {
    AttemptLimitSection(state, actions)
    SecretField(
        value = state.form.currentPassword,
        onValueChange = { v -> actions.onChange { it.copy(currentPassword = v) } },
        labelRes = label,
        mode = state.form.mode,
        kind = SecretKind.PASSWORD,
        enabled = state.passwordFieldEnabled,
        isError = state.problem == FormProblem.CURRENT_PASSWORD_INVALID,
    )
}

/** The attempt limit comes before any password field: read it, show it, and ask for the acknowledgement (W2). */
@Composable
private fun AttemptLimitSection(state: SecurityUiState, actions: SecurityFormActions) {
    val read = state.authLimit
    val limit = read?.authLimit
    when {
        read == null -> {
            val mandatory = state.action == SecurityAction.UNLOCK || state.action == SecurityAction.REMOVE_PASSWORD
            Text(
                stringResource(if (mandatory) R.string.security_unlock_reading else R.string.security_limit_check_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.sheet == null) {
                Button(onClick = actions.onProbeLimit, enabled = state.canStart) { Text(stringResource(R.string.security_limit_check)) }
            }
        }
        limit == null -> {
            Text(stringResource(R.string.security_unlock_limit_unknown), color = MaterialTheme.colorScheme.error)
            if (!state.limitUnderstood) {
                Button(onClick = actions.onLimitUnderstood) { Text(stringResource(R.string.security_unlock_understood)) }
            }
        }
        limit > 0 -> {
            Text(stringResource(R.string.security_unlock_limit, limit), color = MaterialTheme.colorScheme.error)
            if (!state.limitUnderstood) {
                Button(onClick = actions.onLimitUnderstood) { Text(stringResource(R.string.security_unlock_understood)) }
            }
        }
    }
}

@Composable
private fun UnlockFields(state: SecurityUiState, actions: SecurityFormActions) {
    CurrentPasswordField(state, actions, R.string.security_form_current_required)
    PasswordAdvancedSection(state.form.mode, actions.onModeChange) {
        ExpectedPackField(state.form, state.problem, actions.onChange)
    }
}

@Composable
private fun ConfigLockFields(state: SecurityUiState, actions: SecurityFormActions) {
    CheckRow(state.form.lockConfig, { v -> actions.onChange { it.copy(lockConfig = v) } }, R.string.security_config_lock_cfglck)
    StepperRow(
        stringResource(R.string.security_config_lock_authlim, state.form.authLimit),
        onMinus = { actions.onChange { it.copy(authLimit = (it.authLimit - 1).coerceAtLeast(0)) } },
        onPlus = { actions.onChange { it.copy(authLimit = (it.authLimit + 1).coerceAtMost(MAX_LIMIT)) } },
    )
    Text(
        stringResource(R.string.security_config_lock_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    CurrentPasswordField(state, actions, R.string.security_form_current)
    PasswordAdvancedSection(state.form.mode, actions.onModeChange)
}

@Composable
private fun LockFields(state: SecurityUiState, actions: SecurityFormActions) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((scope, label) in LOCK_OPTIONS) {
            val enabled = scope == LockScope.NDEF_READ_ONLY || state.advanced
            Row(
                Modifier.fillMaxWidth().selectable(selected = state.form.lockScope == scope, enabled = enabled, role = Role.RadioButton) {
                    actions.onChange { it.copy(lockScope = scope) }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.form.lockScope == scope, onClick = null, enabled = enabled)
                Text(stringResource(label), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (!state.advanced) {
        Text(
            stringResource(R.string.security_lock_advanced_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    CurrentPasswordField(state, actions, R.string.security_form_current)
    PasswordAdvancedSection(state.form.mode, actions.onModeChange)
}

@Composable
private fun FormProblemText(problem: FormProblem?, failure: SecurityFailure?, mode: PasswordMode) {
    val text = when {
        problem != null -> stringResource(problem.messageRes(mode))
        failure != null -> SecurityErrorText.of(failure).messageText()
        else -> null
    }
    text?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
}

@StringRes
private fun FormProblem.messageRes(mode: PasswordMode): Int = when (this) {
    FormProblem.PASSWORD_INVALID -> if (mode == PasswordMode.TEXT) R.string.security_problem_password else R.string.security_problem_password_hex
    FormProblem.PACK_INVALID -> if (mode == PasswordMode.TEXT) R.string.security_problem_pack else R.string.security_problem_pack_hex
    FormProblem.STORED_NOT_ACKNOWLEDGED -> R.string.security_problem_stored
    FormProblem.CURRENT_PASSWORD_INVALID -> if (mode == PasswordMode.TEXT) R.string.security_problem_current else R.string.security_problem_current_hex
    FormProblem.NOTHING_SELECTED -> R.string.security_problem_nothing
}

private const val MAX_LIMIT = 7

private val LOCK_OPTIONS = listOf(
    LockScope.NDEF_READ_ONLY to R.string.security_lock_scope_ndef,
    LockScope.CC_ONLY to R.string.security_lock_scope_cc,
    LockScope.STATIC_PAGES to R.string.security_lock_scope_static,
    LockScope.DYNAMIC_PAGES to R.string.security_lock_scope_dynamic,
    LockScope.FULL to R.string.security_lock_scope_full,
)
