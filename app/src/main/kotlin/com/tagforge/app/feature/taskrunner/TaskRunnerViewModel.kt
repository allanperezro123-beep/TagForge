package com.tagforge.app.feature.taskrunner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ports.SettingsStore
import com.tagforge.domain.ports.TaskActionExecutor
import com.tagforge.domain.ports.TaskRepository
import com.tagforge.domain.task.ActionOutcome
import com.tagforge.domain.task.ActionResult
import com.tagforge.domain.settings.TaskExecutionPolicy
import com.tagforge.domain.task.Capability
import com.tagforge.domain.task.ConfirmedTask
import com.tagforge.domain.task.PlanItem
import com.tagforge.domain.task.RunProgress
import com.tagforge.domain.task.RunTaskUseCase
import com.tagforge.domain.task.TaskError
import com.tagforge.domain.task.TaskFingerprint
import com.tagforge.domain.task.TaskMessageBuilder
import com.tagforge.domain.task.TaskPayload
import com.tagforge.domain.task.TaskPlan
import com.tagforge.domain.task.TaskPlanner
import com.tagforge.domain.task.TaskTrust
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** A task that ran without the sheet. [fingerprint] is set only for "my own tasks": it is what "stop trusting" forgets. */
data class AutoRun(val policy: TaskExecutionPolicy, val fingerprint: String?, val notice: AutoRunNotice? = null)

/** What the summary tells the user after one of its two actions. */
enum class AutoRunNotice { UNTRUSTED, WILL_ASK }

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
    data class Confirming(val plan: TaskPlan, val armed: Boolean = false, val settled: Boolean = true) : TaskRunnerUiState {
        /** [settled] turns true a moment after the sheet appeared, so a sheet swapped in under the finger cannot be pressed. */
        val canRun: Boolean get() = settled && plan.items.any { !it.isBlocked }
        val anyNeedsGrant: Boolean get() = plan.items.any { it.capability is Capability.NeedsGrant }
    }

    data class Running(val plan: TaskPlan, val rows: List<RunRow>, val auto: AutoRun? = null) : TaskRunnerUiState

    /**
     * [autoClose] is true only when every action did what it said; otherwise the summary waits for the user.
     * [auto] is set when the task ran without the sheet because of the policy chosen in Settings.
     */
    data class Finished(
        val plan: TaskPlan,
        val results: List<ActionResult>,
        val autoClose: Boolean,
        val auto: AutoRun? = null,
    ) : TaskRunnerUiState {
        val needsAttention: Boolean get() = !autoClose
    }
}

sealed interface RunnerEvent {
    /** The runner is done (cancelled or finished): the Activity finishes. */
    data object Close : RunnerEvent
}

/**
 * State holder for the task runner (TK-05, TK-06, TK-08). Tag content is untrusted: a payload only ever becomes a
 * [TaskPlan] to confirm. [onRun] is the door to execution for a user who is asked; the only other one is a policy the user
 * chose in Settings ([TaskTrust.autoConfirm]), which still goes through [TaskPlan.confirm]. There is no timer that can
 * confirm, and cancel, back and outside-tap all end in [RunnerEvent.Close] with zero actions executed.
 */
class TaskRunnerViewModel(
    private val executor: TaskActionExecutor,
    private val repository: TaskRepository,
    private val clock: Clock,
    private val settings: SettingsStore,
    private val isForeground: () -> Boolean,
    private val autoCloseMillis: Long = AUTO_CLOSE_MILLIS,
    private val autoRunCloseMillis: Long = AUTO_RUN_CLOSE_MILLIS,
    private val runGuardMillis: Long = RUN_GUARD_MILLIS,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TaskRunnerUiState>(TaskRunnerUiState.Idle)
    val uiState: StateFlow<TaskRunnerUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<RunnerEvent>(Channel.BUFFERED)
    val events: Flow<RunnerEvent> = eventChannel.receiveAsFlow()

    private var runJob: Job? = null
    private var closeJob: Job? = null
    private var guardJob: Job? = null
    private var decideJob: Job? = null
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
        execute(state.plan, confirmed, auto = null)
    }

    private fun execute(plan: TaskPlan, confirmed: ConfirmedTask, auto: AutoRun?) {
        _uiState.value = TaskRunnerUiState.Running(plan, plan.items.map { RunRow(it, RowStatus.Pending) }, auto)
        runJob = viewModelScope.launch {
            val results = RunTaskUseCase(executor, isForeground).run(confirmed, ::onProgress)
            savedTaskId?.let { repository.markRun(it, clock.now()) }
            val autoClose = results.all { it.outcome is ActionOutcome.Done || it.outcome is ActionOutcome.OpenedFallback }
            _uiState.value = TaskRunnerUiState.Finished(plan, results, autoClose, auto)
            // A task that ran by itself keeps its summary on screen a little longer: that is the moment to stop trusting it.
            if (autoClose) closeAfterDelay(if (auto != null) autoRunCloseMillis else autoCloseMillis)
        }
    }

    /** "Stop trusting this task": the next scan of it asks again. */
    fun onUntrust() {
        val state = _uiState.value as? TaskRunnerUiState.Finished ?: return
        val auto = state.auto ?: return
        val fingerprint = auto.fingerprint ?: return
        closeJob?.cancel()
        viewModelScope.launch {
            settings.update { TaskTrust.untrust(it, fingerprint) }
            _uiState.update { if (it is TaskRunnerUiState.Finished) it.copy(auto = auto.copy(fingerprint = null, notice = AutoRunNotice.UNTRUSTED)) else it }
        }
    }

    /** From the "never ask" summary: one tap back to asking before every task. */
    fun onAskAlways() {
        val state = _uiState.value as? TaskRunnerUiState.Finished ?: return
        val auto = state.auto ?: return
        closeJob?.cancel()
        viewModelScope.launch {
            settings.update { it.copy(taskExecution = TaskExecutionPolicy.ASK_ALWAYS) }
            _uiState.update { if (it is TaskRunnerUiState.Finished) it.copy(auto = auto.copy(notice = AutoRunNotice.WILL_ASK)) else it }
        }
    }

    /** Cancel button. Nothing that has not started will ever start. */
    fun onCancel() {
        decideJob?.cancel()
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

    /** Asks, unless the policy chosen in Settings lets this exact task run by itself (see [TaskTrust.autoConfirm]). */
    private fun confirm(payload: TaskPayload) {
        decideJob?.cancel()
        val plan = TaskPlanner.plan(payload, executor::capability)
        decideJob = viewModelScope.launch {
            val current = settings.settings.first()
            if (_uiState.value is TaskRunnerUiState.Running) return@launch
            val token = TaskTrust.autoConfirm(current, plan)
            if (token == null) {
                showSheet(plan)
            } else {
                closeJob?.cancel()
                guardJob?.cancel()
                val fingerprint = if (current.taskExecution == TaskExecutionPolicy.OWN_TASKS) TaskFingerprint.of(plan) else null
                execute(plan, token, AutoRun(current.taskExecution, fingerprint))
            }
        }
    }

    private fun showSheet(plan: TaskPlan) {
        val locked = runGuardMillis > 0
        show(TaskRunnerUiState.Confirming(plan, settled = !locked))
        if (locked) {
            guardJob = viewModelScope.launch {
                delay(runGuardMillis)
                _uiState.update { if (it is TaskRunnerUiState.Confirming) it.copy(settled = true) else it }
            }
        }
    }

    private fun show(state: TaskRunnerUiState) {
        closeJob?.cancel()
        guardJob?.cancel()
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

    private fun closeAfterDelay(millis: Long) {
        closeJob = viewModelScope.launch {
            delay(millis)
            eventChannel.trySend(RunnerEvent.Close)
        }
    }

    override fun onCleared() {
        decideJob?.cancel()
        runJob?.cancel()
        closeJob?.cancel()
    }

    companion object {
        /** How long a fully successful summary stays before the runner closes by itself (design sec. 8). */
        const val AUTO_CLOSE_MILLIS = 1_500L

        /** The summary of a task that ran without asking stays long enough to read it and to stop trusting the task. */
        const val AUTO_RUN_CLOSE_MILLIS = 5_000L

        /**
         * How long a freshly shown (or swapped) confirmation sheet ignores Run (W5): another app that can replace the
         * pending task must not get a tap that was meant for the previous sheet.
         */
        const val RUN_GUARD_MILLIS = 600L
    }
}
