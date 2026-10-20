package com.tagforge.app.feature.tools.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.domain.security.PasswordMode
import com.tagforge.domain.security.SecretKind

/**
 * The technical corner of every password screen, closed by default: the hexadecimal switch (for tags protected by other
 * apps with bytes that have no keyboard character) and whatever [extra] the screen needs (PACK, first protected page...).
 */
@Composable
fun PasswordAdvancedSection(
    mode: PasswordMode,
    onModeChange: (PasswordMode) -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    var open by rememberSaveable { mutableStateOf(mode == PasswordMode.HEX) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { open = !open }) {
            Text(
                stringResource(if (open) R.string.security_adv_hide else R.string.security_adv_show),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (open) {
            SwitchRow(mode == PasswordMode.HEX, { on -> onModeChange(if (on) PasswordMode.HEX else PasswordMode.TEXT) }, R.string.security_adv_hex_switch)
            Text(
                stringResource(R.string.security_adv_hex_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            extra()
        }
    }
}

/** The PACK of a new password: "TF" unless the user needs a specific one. */
@Composable
fun PackField(form: SecurityForm, problem: FormProblem?, onChange: ((SecurityForm) -> SecurityForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SecretField(
            form.pack, { v -> onChange { it.copy(pack = v) } }, R.string.security_form_pack, form.mode, SecretKind.PACK,
            isError = problem == FormProblem.PACK_INVALID, startVisible = true,
        )
        Text(stringResource(R.string.security_adv_pack_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Unlock only, optional: the PACK this tag should answer. A different answer means the tag may be a copy. */
@Composable
fun ExpectedPackField(form: SecurityForm, problem: FormProblem?, onChange: ((SecurityForm) -> SecurityForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SecretField(
            form.expectedPack, { v -> onChange { it.copy(expectedPack = v) } }, R.string.security_adv_expected_pack, form.mode, SecretKind.PACK,
            isError = problem == FormProblem.PACK_INVALID, startVisible = true,
        )
        Text(stringResource(R.string.security_adv_expected_pack_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
