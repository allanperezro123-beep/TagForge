package com.tagforge.app.feature.taskrunner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ports.TaskActionExecutor
import com.tagforge.domain.ports.TaskRepository
import com.tagforge.domain.task.ActionOutcome
import com.tagforge.domain.task.ActionResult
import com.tagforge.domain.task.Capability
import com.tagforge.domain.task.PlanItem
import com.tagforge.domain.task.RunProgress
import com.tagforge.domain.task.RunTaskUseCase
import com.tagforge.domain.task.TaskError
import com.tagforge.domain.task.TaskMessageBuilder
import com.tagforge.domain.task.TaskPayload
import com.tagforge.domain.task.TaskPlan
import com.tagforge.domain.task.TaskPlanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Where one row of the running task stands. */
sealed interface RowStatus {
    data object Pending : RowStatus
    data object Running : RowStatus
    data class Finished(val outcome: ActionOutcome) : RowStatus
}

data class RunRow(val item: PlanItem, val status: RowStatus)

/** Everything the runner's sheet can show. The screen renders this and nothing else. */
sealed interface TaskRunnerUiState {
    /** No intent processed yet, or the user cancelled: nothing on screen. */
    data object Idle : TaskRunnerUiState

    /** The tag was unreadable, too big, from a newer version, or not a task: an error, never a partial run. */
    data class Error(val error: TaskError) : TaskRunnerUiState

    /**
     * The confirmation sheet. [armed] is true once the user pressed Run on a task with an unencrypted HTTP request:
     * the second press is the one that runs it (TK-08).
     */
    data class Confirming(val plan: TaskPlan, val armed: Boolean = false) : TaskRunnerUiState {
        val canRun: Boolean get() = plan.items.any { !it.isBlocked }
        val anyNeedsGrant: Boolean get() = plan.items.any { it.capability is Capability.NeedsGrant }
    }

    data class Running(val plan: TaskPlan, val rows: List<RunRow>) : TaskRunnerUiState

    /** [autoClose] is true only when every action did what it said; otherwise the summary waits for the user. */
    data class Finished(val plan: TaskPlan, val results: List<ActionResult>, val autoClose: Boolean) : TaskRunnerUiState {
        val needsAttention: Boolean get() = !autoClose
    }
}

sealed interface RunnerEvent {
    /** The runner is done (cancelled or finished): the Activity finishes. */
    data object Close : RunnerEvent
}

/**
 * State holder for the task runner (TK-05, TK-06, TK-08). Tag content is untrusted: a payload only ever becomes a
 * [TaskPlan] to confirm. [onRun] is the single door to execution, there is no timer that can confirm, and cancel,
 * back and outside-tap all end in [RunnerEvent.Close] with zero actions executed.
 */
class TaskRunnerViewModel(
    private val executor: TaskActionExecutor,
    private val repository: TaskRepository,
    private val clock: Clock,
    private val isForeground: () -> Boolean,
    private val autoCloseMillis: Long = AUTO_CLOSE_MILLIS,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TaskRunnerUiState>(TaskRunnerUiState.Idle)
    val uiState: StateFlow<TaskRunnerUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<RunnerEvent>(Channel.BUFFERED)
    val events: Flow<RunnerEvent> = eventChannel.receiveAsFlow()

    private var runJob: Job? = null
    private var closeJob: Job? = null
    private var savedTaskId: Long? = null

    /** A scanned or re-scanned tag (cold start or `onNewIntent`). Replaces whatever is pending, but never a run in progress. */
    fun onNdefBytes(raw: Bytes) {
        if (_uiState.value is TaskRunnerUiState.Running) return
        savedTaskId = null
        val decoded = DefaultNdefCodec.decode(raw)
        if (decoded is Outcome.Err) {
            show(TaskRunnerUiState.Error(TaskError.Malformed("not an NDEF message")))
            return
        }
        when (val task = TaskMessageBuilder.extract((decoded as Outcome.Ok).value)) {
            is Outcome.Err -> show(TaskRunnerUiState.Error(task.error))
            is Outcome.Ok -> confirm(task.value)
        }
    }

    /** "Run now" from the task list: same confirmation, then the run is recorded on the saved task. */
    fun onTaskId(id: Long) {
        if (_uiState.value is TaskRunnerUiState.Running) return
        viewModelScope.launch {
            val task = repository.get(id)
            if (task == null) {
                show(TaskRunnerUiState.Error(TaskError.NotATask))
            } else {
                savedTaskId = id
                confirm(task.payload)
            }
        }
    }

    /** The Run button. For a plain-HTTP task the first press only arms the warning; the second one runs. */
    fun onRun() {
        val state = _uiState.value as? TaskRunnerUiState.Confirming ?: return
        if (!state.canRun) return
        if (state.plan.requiresSecondTap && !state.armed) {
            _uiState.value = state.copy(armed = true)
            return
        }
        val confirmed = state.plan.confirm(acknowledgedInsecure = state.armed) ?: return

        _uiState.value = TaskRunnerUiState.Running(state.plan, state.plan.items.map { RunRow(it, RowStatus.Pending) })
        runJob = viewModelScope.launch {
            val results = RunTaskUseCase(executor, isForeground).run(confirmed, ::onProgress)
            savedTaskId?.let { repository.markRun(it, clock.now()) }
            val autoClose = results.all { it.outcome is ActionOutcome.Done || it.outcome is ActionOutcome.OpenedFallback }
            _uiState.value = TaskRunnerUiState.Finished(state.plan, results, autoClose)
            if (autoClose) closeAfterDelay()
        }
    }

    /** Cancel button. Nothing that has not started will ever start. */
    fun onCancel() {
        runJob?.cancel()
        closeJob?.cancel()
        _uiState.value = TaskRunnerUiState.Idle
        eventChannel.trySend(RunnerEvent.Close)
    }

    /** Back press or tap outside the sheet: exactly a cancel. */
    fun onDismiss() = onCancel()

    /** The summary's own Close button. */
    fun onClose() {
        closeJob?.cancel()
        eventChannel.trySend(RunnerEvent.Close)
    }

    private fun confirm(payload: TaskPayload) {
        show(TaskRunnerUiState.Confirming(TaskPlanner.plan(payload, executor::capability)))
    }

    private fun show(state: TaskRunnerUiState) {
        closeJob?.cancel()
        _uiState.value = state
    }

    private fun onProgress(progress: RunProgress) {
        _uiState.update { state ->
            if (state !is TaskRunnerUiState.Running) return@update state
            val rows = state.rows.toMutableList()
            when (progress) {
                is RunProgress.Started -> rows[progress.index] = rows[progress.index].copy(status = RowStatus.Running)
                is RunProgress.Finished -> rows[progress.index] = rows[progress.index].copy(status = RowStatus.Finished(progress.outcome))
            }
            state.copy(rows = rows)
        }
    }

    private fun closeAfterDelay() {
        closeJob = viewModelScope.launch {
            delay(autoCloseMillis)
            eventChannel.trySend(RunnerEvent.Close)
        }
    }

    override fun onCleared() {
        runJob?.cancel()
        closeJob?.cancel()
    }

    companion object {
        /** How long a fully successful summary stays before the runner closes by itself (design sec. 8). */
        const val AUTO_CLOSE_MILLIS = 1_500L
    }
}
