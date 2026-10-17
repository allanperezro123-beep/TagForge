package com.tagforge.app.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.components.BackNavigationIcon
import com.tagforge.app.ui.components.ConfirmDialog
import com.tagforge.app.ui.components.EmptyState
import com.tagforge.app.ui.components.SkeletonList
import com.tagforge.app.ui.theme.extendedColors
import com.tagforge.domain.history.HistoryEntry

private val ROW_HEIGHT = 72.dp

/**
 * Presentational history list. While [HistoryUiState.loading] it shows skeleton rows of the real row height, so the
 * swap to content causes no layout shift; with no entries it explains itself and offers to read a tag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onClearRequested: () -> Unit,
    onClearConfirmed: () -> Unit,
    onClearDismissed: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = { BackNavigationIcon(onBack) },
                actions = {
                    if (state.entries.isNotEmpty()) {
                        IconButton(onClick = onClearRequested) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.history_clear))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList(Modifier.padding(16.dp), rowHeight = ROW_HEIGHT)
                state.isEmpty -> EmptyState(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.history_empty_title),
                    body = stringResource(R.string.history_empty_body),
                    action = { Button(onClick = onScan) { Text(stringResource(R.string.history_empty_action)) } },
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(state.entries, key = { it.id }) { entry ->
                        HistoryRow(entry, onOpen = { onOpen(entry.id) }, onDelete = { onDelete(entry.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (state.confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.history_clear_title),
            body = stringResource(R.string.history_clear_body),
            confirmLabel = stringResource(R.string.history_clear_confirm),
            destructive = true,
            onConfirm = onClearConfirmed,
            onDismiss = onClearDismissed,
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onOpen: () -> Unit, onDelete: () -> Unit) {
    val kind = stringResource(entry.kind.labelRes())
    val outcome = stringResource(entry.outcome.labelRes())
    val whenText = rememberDateText(entry.at)
    val summary = entry.summary.ifEmpty { stringResource(R.string.history_empty_summary) }
    val tagType = entry.tagType ?: stringResource(R.string.common_unknown)
    val description = stringResource(R.string.history_row_description, kind, summary, outcome, whenText)

    ListItem(
        modifier = Modifier.clickable(onClick = onOpen).semantics { contentDescription = description },
        leadingContent = {
            // Icon AND the outcome word in the second line: the result is never colour alone.
            if (entry.outcome.isSuccess) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.extendedColors.success)
            } else {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        },
        headlineContent = { Text(summary, maxLines = 1) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.history_row_meta, kind, outcome), style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.history_row_meta, tagType, whenText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.history_delete))
            }
        },
    )
}
