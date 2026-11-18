package com.tagforge.app.feature.tools.security

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.theme.extendedColors
import com.tagforge.domain.security.AuthLimitGuidance
import com.tagforge.domain.security.AuthLimitMeaning
import com.tagforge.domain.security.DiversifiedPassword
import com.tagforge.domain.security.PassphraseProblem
import com.tagforge.domain.security.PasswordStrength
import com.tagforge.domain.security.StrengthLevel
import com.tagforge.domain.security.StrengthReason

/**
 * What the strength of the typed password is, why, and the hard ceiling that no choice can lift: an NTAG21x password is 32
 * bits sent in clear. The ceiling is shown even before anything valid was typed, so nobody reads "best" as "secure".
 */
@Composable
fun PasswordStrengthSection(strength: PasswordStrength?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (strength != null) {
            val color = when (strength.level) {
                StrengthLevel.WEAK -> MaterialTheme.colorScheme.error
                StrengthLevel.FAIR -> MaterialTheme.colorScheme.tertiary
                StrengthLevel.BEST_POSSIBLE -> MaterialTheme.extendedColors.success
            }
            Text(stringResource(strength.level.titleRes()), color = color, style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(R.string.security_strength_bits, strength.estimatedBits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (reason in strength.reasons) {
                Text(stringResource(reason.messageRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.security_strength_ceiling), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@StringRes
private fun StrengthLevel.titleRes(): Int = when (this) {
    StrengthLevel.WEAK -> R.string.security_strength_weak
    StrengthLevel.FAIR -> R.string.security_strength_fair
    StrengthLevel.BEST_POSSIBLE -> R.string.security_strength_best
}

@StringRes
private fun StrengthReason.messageRes(): Int = when (this) {
    StrengthReason.ALL_ZERO -> R.string.security_strength_r_all_zero
    StrengthReason.ALL_SAME_BYTE -> R.string.security_strength_r_all_same
    StrengthReason.FACTORY_DEFAULT -> R.string.security_strength_r_factory
    StrengthReason.SEQUENTIAL_BYTES -> R.string.security_strength_r_sequential
    StrengthReason.REPEATED_PAIR -> R.string.security_strength_r_repeated
    StrengthReason.COMMON_PATTERN -> R.string.security_strength_r_common
    StrengthReason.ONLY_DIGITS -> R.string.security_strength_r_digits
    StrengthReason.ONLY_LOWERCASE -> R.string.security_strength_r_lower
    StrengthReason.ONLY_UPPERCASE -> R.string.security_strength_r_upper
    StrengthReason.TEXT_SMALLER_SPACE -> R.string.security_strength_r_text
}

/** AUTHLIM in attempts, with the warning for an unlimited tag and the recommended middle value (WU4). */
@Composable
fun AuthLimitGuidanceText(authLimit: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (val meaning = AuthLimitGuidance.meaning(authLimit)) {
            AuthLimitMeaning.LeavesUnchanged ->
                Text(stringResource(R.string.security_authlim_zero), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            is AuthLimitMeaning.AtMostAttempts ->
                Text(stringResource(R.string.security_authlim_attempts, meaning.attempts), style = MaterialTheme.typography.bodySmall)
        }
        Text(
            stringResource(R.string.security_authlim_recommended),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The toggle that swaps the 4-byte field for a master passphrase; [extra] is what the screen shows once it is on. */
@Composable
fun DeriveSwitch(derive: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SwitchRow(derive, onChange, R.string.security_derive_switch)
        if (derive) {
            Text(stringResource(R.string.security_derive_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The master passphrase, hidden as dots until the user taps Show. It is never persisted: it lives in the form only. */
@Composable
fun PassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    val problem = if (value.isEmpty()) null else DiversifiedPassword.check(value)
    val definitive = problem?.takeIf { it !is PassphraseProblem.TooShort }
    val counter = stringResource(R.string.security_passphrase_count, value.length, DiversifiedPassword.MIN_PASSPHRASE_LENGTH)
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.security_passphrase), maxLines = 1) },
            supportingText = { Text(definitive?.let { stringResource(it.messageRes(), DiversifiedPassword.MIN_PASSPHRASE_LENGTH) } ?: counter) },
            singleLine = true,
            enabled = enabled,
            isError = isError || definitive != null,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
        )
        TextButton(onClick = { visible = !visible }) {
            Text(stringResource(if (visible) R.string.security_form_hide else R.string.security_form_show), maxLines = 1)
        }
    }
}

@StringRes
private fun PassphraseProblem.messageRes(): Int = when (this) {
    is PassphraseProblem.TooShort -> R.string.security_passphrase_too_short
    PassphraseProblem.TooFewDistinctCharacters -> R.string.security_passphrase_few
    PassphraseProblem.Repetitive -> R.string.security_passphrase_repetitive
    PassphraseProblem.Sequential -> R.string.security_passphrase_sequential
    PassphraseProblem.Common -> R.string.security_passphrase_common
}
