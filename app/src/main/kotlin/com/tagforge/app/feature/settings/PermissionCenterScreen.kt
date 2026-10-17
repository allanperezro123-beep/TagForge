package com.tagforge.app.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.components.BackNavigationIcon
import com.tagforge.app.ui.theme.extendedColors

/** Name and one-line reason of each permission, shared with the confirmation sheet. */
@StringRes
fun PermissionKind.titleRes(): Int = when (this) {
    PermissionKind.WRITE_SETTINGS -> R.string.permissions_write_settings
    PermissionKind.NOTIFICATION_POLICY -> R.string.permissions_policy
    PermissionKind.NOTIFICATIONS -> R.string.permissions_notifications
    PermissionKind.NFC -> R.string.permissions_nfc
    PermissionKind.ALARMS -> R.string.permissions_alarms
}

@StringRes
fun PermissionKind.reasonRes(): Int = when (this) {
    PermissionKind.WRITE_SETTINGS -> R.string.permissions_write_settings_reason
    PermissionKind.NOTIFICATION_POLICY -> R.string.permissions_policy_reason
    PermissionKind.NOTIFICATIONS -> R.string.permissions_notifications_reason
    PermissionKind.NFC -> R.string.permissions_nfc_reason
    PermissionKind.ALARMS -> R.string.permissions_alarms_reason
}

/** Stateless Permission Center: one row per permission with a status (icon AND text), a reason and a Grant button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionCenterScreen(
    state: PermissionCenterUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onGrant: (PermissionKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permissions_title)) },
                navigationIcon = { BackNavigationIcon(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.permissions_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            state.rows.forEach { row -> PermissionRowItem(row, onGrant = { onGrant(row.kind) }) }
        }
    }
}

@Composable
private fun PermissionRowItem(row: PermissionRow, onGrant: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(row.kind.titleRes())) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(row.kind.reasonRes()))
                StatusChip(row)
            }
        },
        trailingContent = {
            if (row.canGrant) {
                OutlinedButton(onClick = onGrant, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.permissions_grant))
                }
            }
        },
    )
}

/** Status is an icon plus words, never colour alone. */
@Composable
private fun StatusChip(row: PermissionRow) {
    val (icon, tint, label) = when (row.state) {
        PermissionState.GRANTED -> Triple(Icons.Outlined.CheckCircle, MaterialTheme.extendedColors.success, R.string.permissions_state_granted)
        PermissionState.NEEDS_GRANT -> Triple(Icons.Outlined.ErrorOutline, MaterialTheme.extendedColors.warning, R.string.permissions_state_needs_grant)
        PermissionState.NOT_AVAILABLE -> Triple(
            Icons.Outlined.RemoveCircleOutline,
            MaterialTheme.colorScheme.onSurfaceVariant,
            if (row.kind == PermissionKind.NFC) R.string.permissions_state_no_nfc else R.string.permissions_state_unavailable,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = tint)
    }
}
