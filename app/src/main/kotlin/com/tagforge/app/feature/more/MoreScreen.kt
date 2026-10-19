package com.tagforge.app.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tagforge.app.R

/** Presentational "More" menu: entry points to the screens that do not deserve a bottom-bar slot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpenHistory: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenImportExport: () -> Unit,
    onOpenBatch: () -> Unit,
    onOpenQr: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.more_title)) }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            MoreEntry(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.more_history),
                summary = stringResource(R.string.more_history_summary),
                onClick = onOpenHistory,
            )
            MoreEntry(
                icon = Icons.Outlined.Bookmarks,
                title = stringResource(R.string.more_templates),
                summary = stringResource(R.string.more_templates_summary),
                onClick = onOpenTemplates,
            )
            MoreEntry(
                icon = Icons.Outlined.Layers,
                title = stringResource(R.string.more_batch),
                summary = stringResource(R.string.more_batch_summary),
                onClick = onOpenBatch,
            )
            MoreEntry(
                icon = Icons.Outlined.QrCode2,
                title = stringResource(R.string.more_qr),
                summary = stringResource(R.string.more_qr_summary),
                onClick = onOpenQr,
            )
            MoreEntry(
                icon = Icons.Outlined.SwapVert,
                title = stringResource(R.string.more_import_export),
                summary = stringResource(R.string.more_import_export_summary),
                onClick = onOpenImportExport,
            )
            MoreEntry(
                icon = Icons.Outlined.Security,
                title = stringResource(R.string.more_permissions),
                summary = stringResource(R.string.more_permissions_summary),
                onClick = onOpenPermissions,
            )
            MoreEntry(
                icon = Icons.Outlined.Settings,
                title = stringResource(R.string.more_settings),
                summary = stringResource(R.string.more_settings_summary),
                onClick = onOpenSettings,
            )
            MoreEntry(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.more_about),
                summary = stringResource(R.string.more_about_summary),
                onClick = onOpenAbout,
            )
        }
    }
}

@Composable
private fun MoreEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
    )
}
