package com.tagforge.app.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.feature.taskrunner.RiskBadge
import com.tagforge.app.ui.components.ConfirmDialog
import com.tagforge.app.ui.components.EmptyState
import com.tagforge.app.ui.components.SkeletonList
import com.tagforge.domain.task.Task

/** Presentational saved-task list. Running a task only opens the confirmation sheet; nothing executes from here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    state: TasksUiState,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
    onRun: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onDeleteRequested: (Long) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tasks_title)) }) },
        floatingActionButton = {
            if (!state.isEmpty) {
                ExtendedFloatingActionButton(
                    onClick = onNew,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.tasks_new)) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList(Modifier.padding(16.dp))
                state.isEmpty -> EmptyState(
                    icon = Icons.Outlined.TaskAlt,
                    title = stringResource(R.string.tasks_empty_title),
                    body = stringResource(R.string.tasks_empty_body),
                    action = { Button(onClick = onNew) { Text(stringResource(R.string.tasks_empty_action)) } },
                )
                else -> LazyColumn {
                    items(state.tasks, key = { it.id }) { task ->
                        TaskRow(task, onOpen = { onOpen(task.id) }, onRun = { onRun(task.id) }, onDuplicate = { onDuplicate(task.id) }, onDelete = { onDeleteRequested(task.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    state.deleteCandidate?.let { task ->
        ConfirmDialog(
            title = stringResource(R.string.tasks_delete_title),
            body = stringResource(R.string.tasks_delete_body, task.name.ifBlank { stringResource(R.string.tasks_unnamed) }),
            confirmLabel = stringResource(R.string.tasks_delete_confirm),
            destructive = true,
            onConfirm = onDeleteConfirmed,
            onDismiss = onDeleteCancelled,
        )
    }
}

@Composable
private fun TaskRow(task: Task, onOpen: () -> Unit, onRun: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val name = task.name.ifBlank { stringResource(R.string.tasks_unnamed) }
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(pluralStringResource(R.plurals.tasks_actions, task.actions.size, task.actions.size))
                val risks = TaskRiskSummary.of(task.actions)
                if (risks.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { risks.forEach { RiskBadge(it) } }
                }
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onRun) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.tasks_run) + ": " + name)
                }
                Column {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.tasks_menu, name))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tasks_edit)) },
                            onClick = { menuOpen = false; onOpen() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tasks_duplicate)) },
                            onClick = { menuOpen = false; onDuplicate() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tasks_delete)) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        },
    )
}
