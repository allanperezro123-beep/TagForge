package com.tagforge.app.feature.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagforge.app.di.tagForgeViewModel
import com.tagforge.app.feature.taskrunner.TaskRunnerActivity

/**
 * Container for the Tasks tab. "Run" hands the task to [TaskRunnerActivity], which shows the confirmation sheet:
 * nothing runs from this screen without that confirmation.
 */
@Composable
fun TasksRoute(onNew: () -> Unit, onOpen: (Long) -> Unit) {
    val viewModel = tagForgeViewModel { TasksViewModel(it.taskRepository, it.clock) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TasksEvent.OpenRunner -> context.startActivity(TaskRunnerActivity.forSavedTask(context, event.taskId))
            }
        }
    }

    TaskListScreen(
        state = state,
        onNew = onNew,
        onOpen = onOpen,
        onRun = viewModel::onRunNow,
        onDuplicate = viewModel::onDuplicate,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteCancelled = viewModel::onDeleteCancelled,
    )
}
