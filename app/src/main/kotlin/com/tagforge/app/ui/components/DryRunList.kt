package com.tagforge.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.feature.tools.labelRes
import com.tagforge.domain.security.DryRunLine
import com.tagforge.domain.security.IrreversibleEffect

@StringRes
fun IrreversibleEffect.messageRes(): Int = when (this) {
    IrreversibleEffect.PASSWORD_PROTECTS_TAG -> R.string.effect_password_protects
    IrreversibleEffect.WRITE_PROTECTED_ONLY -> R.string.effect_write_protected_only
    IrreversibleEffect.CONFIG_PAGES_LOCKED_FOREVER -> R.string.effect_config_locked
    IrreversibleEffect.AUTH_ATTEMPTS_LIMITED -> R.string.effect_auth_limited
    IrreversibleEffect.LOCK_BITS_NEVER_CLEARED -> R.string.effect_lock_bits
    IrreversibleEffect.TAG_READ_ONLY_FOREVER -> R.string.effect_read_only_forever
    IrreversibleEffect.RAW_RESERVED_PAGE_WRITE -> R.string.effect_raw_reserved
}

/** The exact frames of a plan, one block per page, with the page content before and after (NS-01 step 1). */
@Composable
fun DryRunList(lines: List<DryRunLine>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (line in lines) {
            Column {
                val title = line.region?.let { stringResource(R.string.typed_confirm_line_page, line.page, stringResource(it.labelRes())) }
                    ?: stringResource(R.string.typed_confirm_line_page_plain, line.page)
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(line.commandHex, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                Text(
                    line.oldHex?.let { stringResource(R.string.typed_confirm_before, it) } ?: stringResource(R.string.typed_confirm_before_hidden),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (line.newHex.isNotEmpty()) {
                    Text(
                        stringResource(R.string.typed_confirm_after, line.newHex),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Each irreversible effect as a sentence with a warning icon: the meaning never relies on colour alone. */
@Composable
fun EffectList(effects: Collection<IrreversibleEffect>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (effect in effects) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp))
                Text(stringResource(effect.messageRes()), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
