package com.tagforge.app.feature.tools.security

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tagforge.app.R

/** The security hub: one row per operation. The raw console row exists only when Advanced mode is on (AS-07). */
@Composable
fun SecurityChooseList(
    advanced: Boolean,
    enabled: Boolean,
    onChoose: (SecurityAction) -> Unit,
    onOpenSignature: () -> Unit,
    onOpenConsole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            stringResource(R.string.security_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        HubEntry(Icons.Outlined.Password, R.string.security_action_set_password, R.string.security_action_set_password_summary, enabled) { onChoose(SecurityAction.SET_PASSWORD) }
        HubEntry(Icons.Outlined.LockOpen, R.string.security_action_unlock, R.string.security_action_unlock_summary, enabled) { onChoose(SecurityAction.UNLOCK) }
        HubEntry(Icons.Outlined.VerifiedUser, R.string.security_action_remove_password, R.string.security_action_remove_password_summary, enabled, danger = true) { onChoose(SecurityAction.REMOVE_PASSWORD) }
        HubEntry(Icons.Outlined.Lock, R.string.security_action_config_lock, R.string.security_action_config_lock_summary, enabled, danger = true) { onChoose(SecurityAction.CONFIG_LOCK) }
        HubEntry(Icons.Outlined.Lock, R.string.security_action_lock, R.string.security_action_lock_summary, enabled, danger = true) { onChoose(SecurityAction.LOCK) }
        HubEntry(Icons.Outlined.Fingerprint, R.string.security_action_signature, R.string.security_action_signature_summary, true, onClick = onOpenSignature)
        if (advanced) {
            HubEntry(Icons.Outlined.Terminal, R.string.security_action_console, R.string.security_action_console_summary, true, onClick = onOpenConsole)
        }
    }
}

@Composable
private fun HubEntry(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes summary: Int,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null, tint = if (enabled) tint else MaterialTheme.colorScheme.outline) },
        headlineContent = { Text(stringResource(title), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline) },
        supportingContent = { Text(stringResource(summary)) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
    )
}
