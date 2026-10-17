package com.tagforge.app.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.domain.ports.TaskRepository
import com.tagforge.domain.task.Task
import com.tagforge.domain.template.TemplateNames
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class TasksUiState(
    val loading: Boolean = true,
    val tasks: List<Task> = emptyList(),
    /** Set while the "delete this task?" dialog is open. */
    val deleteCandidate: Task? = null,
) {
    val isEmpty: Boolean get() = !loading && tasks.isEmpty()
}

sealed interface TasksEvent {
    /** Open the task runner for a saved task. It shows the confirmation sheet; nothing runs until the user confirms. */
    data class OpenRunner(val taskId: Long) : TasksEvent
}

/** State holder for the saved-tasks list (TK-10): duplicate, delete (with confirmation) and run now. */
class TasksViewModel(
    private val repository: TaskRepository,
    private val clock: Clock,
) : ViewModel() {

    private val deleteCandidate = MutableStateFlow<Task?>(null)

    private val eventChannel = Channel<TasksEvent>(Channel.BUFFERED)
    val events: Flow<TasksEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<TasksUiState> = combine(repository.observeAll(), deleteCandidate) { tasks, candidate ->
        TasksUiState(loading = false, tasks = tasks, deleteCandidate = candidate)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TasksUiState())

    /** A copy with the same actions and a unique name ("Night" -> "Night (2)"). */
    fun onDuplicate(id: Long) {
        viewModelScope.launch {
            val original = repository.get(id) ?: return@launch
            val taken = repository.observeAll().first().map { it.name }
            val name = TemplateNames.resolve(original.name, taken) ?: return@launch
            val now = clock.now()
            repository.upsert(Task(name = name, actions = original.actions, createdAt = now, updatedAt = now))
        }
    }

    /** First step of the destructive protocol: ask. Nothing is deleted yet. */
    fun onDeleteRequested(id: Long) {
        viewModelScope.launch { deleteCandidate.value = repository.get(id) }
    }

    fun onDeleteCancelled() {
        deleteCandidate.value = null
    }

    fun onDeleteConfirmed() {
        val task = deleteCandidate.value ?: return
        deleteCandidate.value = null
        viewModelScope.launch { repository.delete(task.id) }
    }

    fun onRunNow(id: Long) {
        eventChannel.trySend(TasksEvent.OpenRunner(id))
    }
}
