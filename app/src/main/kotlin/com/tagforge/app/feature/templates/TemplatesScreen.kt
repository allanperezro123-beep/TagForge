package com.tagforge.app.feature.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.feature.write.kind
import com.tagforge.app.feature.write.titleRes
import com.tagforge.app.ui.components.BackNavigationIcon
import com.tagforge.app.ui.components.EmptyState
import com.tagforge.app.ui.components.SkeletonList
import com.tagforge.domain.template.Template

/** Presentational template list: tap a row to start the wizard from it; the overflow menu renames or deletes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    state: TemplatesUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onGoToWrite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by rememberSaveable { mutableStateOf<Long?>(null) }
    val renamingTemplate = state.templates.firstOrNull { it.id == renaming }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.templates_title)) },
                navigationIcon = { BackNavigationIcon(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList(Modifier.padding(16.dp))
                state.isEmpty -> EmptyState(
                    icon = Icons.Outlined.Bookmarks,
                    title = stringResource(R.string.templates_empty_title),
                    body = stringResource(R.string.templates_empty_body),
                    action = { Button(onClick = onGoToWrite) { Text(stringResource(R.string.templates_empty_action)) } },
                )
                else -> LazyColumn {
                    items(state.templates, key = { it.id }) { template ->
                        TemplateRow(
                            template,
                            onOpen = { onOpen(template.id) },
                            onRename = { renaming = template.id },
                            onDelete = { onDelete(template.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (renamingTemplate != null) {
        RenameDialog(
            current = renamingTemplate.name,
            onConfirm = { name ->
                onRename(renamingTemplate.id, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun TemplateRow(template: Template, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val kinds = template.records.map { stringResource(it.kind().titleRes()) }.distinct().joinToString(", ")

    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null) },
        headlineContent = { Text(template.name, maxLines = 1) },
        supportingContent = {
            val count = pluralStringResource(R.plurals.templates_records, template.records.size, template.records.size)
            Text(if (kinds.isEmpty()) count else "$count · $kinds", maxLines = 1)
        },
        trailingContent = {
            Column {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.templates_menu, template.name))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.templates_rename)) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.templates_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun RenameDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.templates_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.write_template_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
