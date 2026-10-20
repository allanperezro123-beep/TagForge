package com.tagforge.app.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.app.feature.write.OverwritePrompt
import com.tagforge.app.feature.write.WriteSheet
import com.tagforge.app.feature.write.WriteTarget
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.history.HistorySummarizer
import com.tagforge.domain.history.RecordHistoryUseCase
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefBudget
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ports.SettingsStore
import com.tagforge.domain.ports.TaskRepository
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.scan.ScanState
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.task.Task
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.task.TaskDraft
import com.tagforge.domain.task.TaskMessageBuilder
import com.tagforge.domain.task.TaskPayload
import com.tagforge.domain.task.TaskTrust
import com.tagforge.domain.template.TemplateNames
import com.tagforge.domain.write.WriteNdefUseCase
import com.tagforge.domain.write.WriteReport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * State holder for the task builder: the ordered actions with their forms, the live size meter against a target tag,
 * saving (TK-10) and writing the task to a tag (TK-04). Writing goes through the same pipeline as every other write:
 * [WriteNdefUseCase] with read-back verification and the overwrite confirmation (G5).
 */
class TaskBuilderViewModel(
    private val coordinator: ScanCoordinator,
    private val settings: SettingsStore,
    private val recordHistory: RecordHistoryUseCase,
    private val tasks: TaskRepository,
    private val clock: Clock,
    private val appPackage: String,
    availability: Flow<NfcAvailability>,
    taskId: Long? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(derive(TaskBuilderUiState(loading = taskId != null, analysis = analyse(TaskDraft(), 0))))
    val uiState: StateFlow<TaskBuilderUiState> = _state.asStateFlow()

    private val eventChannel = Channel<TaskBuilderEvent>(Channel.BUFFERED)
    val events: Flow<TaskBuilderEvent> = eventChannel.receiveAsFlow()

    private var armJob: Job? = null
    private var armedOperation: TagOperation<*>? = null
    private var pendingDecision: CompletableDeferred<Boolean>? = null
    private var lastMessage: NdefMessage? = null
    private var removed: Pair<Int, TaskAction>? = null

    init {
        viewModelScope.launch { availability.collect { value -> mutate { it.copy(availability = value) } } }
        viewModelScope.launch {
            val remembered = settings.settings.first().lastTargetModel
            mutate { if (it.target is WriteTarget.Chip) it.copy(target = WriteTarget.Chip(remembered)) else it }
        }
        if (taskId != null) {
            viewModelScope.launch {
                val task = tasks.get(taskId)
                mutate {
                    if (task == null) it.copy(loading = false) else it.copy(loading = false, taskId = task.id, name = task.name, actions = task.actions)
                }
            }
        }
    }

    // ---- editing the task ---------------------------------------------------------------------------------------

    fun onNameChanged(name: String) = mutate { it.copy(name = name, nameError = false) }

    /** Opens the picker. A full task (16 actions) does not grow. */
    fun onAddAction() = mutate { if (it.isFull) it else it.copy(picking = true) }

    fun onDismissPicker() = mutate { it.copy(picking = false) }

    fun onKindSelected(kind: ActionKind) = mutate {
        it.copy(picking = false, editing = EditingAction(index = null, action = kind.newAction()))
    }

    fun onActionChanged(action: TaskAction) = mutate { state -> state.editing?.let { state.copy(editing = it.copy(action = action)) } ?: state }

    /** Adds or replaces the action being edited. A blocked action stays in its form with the reason shown. */
    fun onConfirmAction() = mutate { state ->
        val editing = state.editing ?: return@mutate state
        if (!editing.isValid) return@mutate state.copy(editing = editing.copy(showAllErrors = true))
        removed = null
        val draft = TaskDraft(state.name, state.actions)
        val next = if (editing.index == null) draft.add(editing.action) else draft.replace(editing.index, editing.action)
        state.copy(actions = next.actions, editing = null, canUndoRemove = false)
    }

    fun onEditAction(index: Int) = mutate { state ->
        val action = state.actions.getOrNull(index) ?: return@mutate state
        state.copy(editing = EditingAction(index, action))
    }

    fun onCancelEditing() = mutate { it.copy(editing = null) }

    fun onRemoveAction(index: Int) {
        mutate { state ->
            val action = state.actions.getOrNull(index) ?: return@mutate state
            removed = index to action
            state.copy(actions = TaskDraft(state.name, state.actions).remove(index).actions, canUndoRemove = true)
        }
        if (removed != null) eventChannel.trySend(TaskBuilderEvent.ActionRemoved)
    }

    fun onUndoRemove() = mutate { state ->
        val (index, action) = removed ?: return@mutate state
        removed = null
        val actions = state.actions.toMutableList().also { it.add(index.coerceAtMost(it.size), action) }
        state.copy(actions = actions, canUndoRemove = false)
    }

    fun onMoveAction(index: Int, delta: Int) = mutate { it.copy(actions = TaskDraft(it.name, it.actions).move(index, delta).actions) }

    fun onTargetSelected(target: WriteTarget) {
        mutate { it.copy(target = target) }
        if (target is WriteTarget.Chip) {
            viewModelScope.launch { settings.update { it.copy(lastTargetModel = target.model) } }
        }
    }

    /** Back press: closes the form or the picker if one is open. False lets the navigation host leave the screen. */
    fun onBack(): Boolean {
        val state = _state.value
        return when {
            state.editing != null -> { onCancelEditing(); true }
            state.picking -> { onDismissPicker(); true }
            else -> false
        }
    }

    // ---- saving (TK-10) -------------------------------------------------------------------------------------------

    /** Stores the task under its (unique) name. A task without a name is not saved: the name field shows why. */
    fun onSave() {
        val state = _state.value
        if (!state.canSave) return
        if (state.name.isBlank()) {
            mutate { it.copy(nameError = true) }
            return
        }
        viewModelScope.launch {
            val taken = tasks.observeAll().first().filter { it.id != state.taskId }.map { it.name }
            val name = TemplateNames.resolve(state.name, taken) ?: return@launch
            val now = clock.now()
            val existing = state.taskId?.let { tasks.get(it) }
            val saved = Task(
                id = existing?.id ?: 0,
                name = name,
                actions = state.actions,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                lastRunAt = existing?.lastRunAt,
            )
            val id = tasks.upsert(saved)
            rememberAsMine(saved.payload)
            mutate { it.copy(taskId = id, name = name) }
            eventChannel.trySend(TaskBuilderEvent.Saved(name))
        }
    }

    // ---- writing (TK-04) --------------------------------------------------------------------------------------------

    fun onWrite() {
        val state = _state.value
        if (!state.canWrite) return
        val message = state.analysis.message ?: return
        lastMessage = message
        arm(message)
    }

    fun onRetry() {
        lastMessage?.let(::arm)
    }

    fun onOverwriteDecision(confirmed: Boolean) {
        pendingDecision?.complete(confirmed)
    }

    /**
     * Closing the sheet. While "replace existing content?" is open it means "no"; while a tag is being written it is
     * ignored, because aborting mid-write is what the write ordering protects against.
     */
    fun onSheetDismissed() {
        val state = _state.value
        if (state.overwrite != null) {
            pendingDecision?.complete(false)
            return
        }
        if (state.sheet is WriteSheet.Working) return
        armJob?.cancel()
        disarm()
        mutate { it.copy(sheet = null, overwrite = null) }
    }

    /** The screen left: stop waiting for a tag, but never abort a write that is already running. */
    fun onHidden() {
        if (_state.value.sheet is WriteSheet.Working) return
        armJob?.cancel()
        pendingDecision?.complete(false)
        disarm()
        mutate { it.copy(sheet = null, overwrite = null) }
    }

    override fun onCleared() {
        pendingDecision?.complete(false)
        disarm()
    }

    private fun disarm() {
        armedOperation?.let(coordinator::disarmIfCurrent)
        armedOperation = null
    }

    private fun arm(message: NdefMessage) {
        armJob?.cancel()
        pendingDecision?.complete(false)
        val operation = WriteNdefUseCase(message) { existing -> askToOverwrite(existing) }
        armedOperation = operation
        val scan = coordinator.arm(operation)
        mutate { it.copy(sheet = WriteSheet.Waiting(DefaultNdefCodec.encode(message).size), overwrite = null) }

        var detected = false
        armJob = viewModelScope.launch {
            scan.collect { scanState ->
                if (scanState is ScanState.Working && !detected) {
                    detected = true
                    eventChannel.trySend(TaskBuilderEvent.TagDetected)
                }
                handle(scanState, message)
            }
        }
    }

    /** Runs inside the write operation: suspends until the user answers the dialog. */
    private suspend fun askToOverwrite(existing: NdefMessage?): Boolean {
        if (!settings.settings.first().confirmOverwrite) return true // the user turned the question off in Settings
        val decision = CompletableDeferred<Boolean>()
        pendingDecision = decision
        val summary = existing?.let(HistorySummarizer::summarize)?.ifEmpty { null }
        mutate { it.copy(overwrite = OverwritePrompt(summary)) }
        return try {
            decision.await()
        } finally {
            if (pendingDecision === decision) pendingDecision = null
            mutate { it.copy(overwrite = null) }
        }
    }

    private suspend fun handle(scan: ScanState<WriteReport>, message: NdefMessage) {
        when (scan) {
            ScanState.Idle -> Unit
            is ScanState.Waiting -> Unit
            is ScanState.Working -> mutate { it.copy(sheet = WriteSheet.Working(scan.phase)) }
            is ScanState.Success -> {
                mutate { it.copy(sheet = WriteSheet.Success(scan.value), target = capacityTarget(it.target, scan.tag.ndef?.maxSize)) }
                eventChannel.trySend(TaskBuilderEvent.WriteSucceeded)
                // Read back what is now on the tag: that is exactly what a later scan will decode.
                (TaskMessageBuilder.extract(message) as? Outcome.Ok)?.let { rememberAsMine(it.value) }
                recordHistory.recordWrite(scan.tag, message, error = null)
            }
            is ScanState.Failure -> {
                val error = scan.error
                if (error == TagError.Cancelled) {
                    mutate { it.copy(sheet = null, overwrite = null) }
                    return
                }
                mutate { state ->
                    val tooSmall = error as? TagError.CapacityExceeded
                    state.copy(
                        sheet = WriteSheet.Failure(error, canRetry = error != TagError.ReadOnly && error !is TagError.CapacityExceeded),
                        target = if (tooSmall != null) WriteTarget.LastTag(tooSmall.available) else state.target,
                    )
                }
                eventChannel.trySend(TaskBuilderEvent.WriteFailed)
                recordHistory.recordWrite(scan.tag, message, error)
            }
        }
    }

    /** A task created here is remembered by its fingerprint, so "run my own tasks without asking" can recognise it later. */
    private suspend fun rememberAsMine(payload: TaskPayload) {
        settings.update { TaskTrust.trust(it, payload.name, payload.actions) }
    }

    /** After a successful write the meter keeps measuring against the tag that was just used, when its size is known. */
    private fun capacityTarget(current: WriteTarget, maxSize: Int?): WriteTarget = if (maxSize != null && current is WriteTarget.LastTag) WriteTarget.LastTag(maxSize) else current

    // ---- derived state ------------------------------------------------------------------------------------------------

    private fun mutate(transform: (TaskBuilderUiState) -> TaskBuilderUiState) {
        _state.update { derive(transform(it)) }
    }

    private fun derive(state: TaskBuilderUiState): TaskBuilderUiState {
        val capacity = when (val target = state.target) {
            is WriteTarget.Chip -> NdefBudget.androidMaxSize(target.model)
            is WriteTarget.LastTag -> target.maxSize
        }
        val analysis = analyse(TaskDraft(state.name, state.actions), capacity)
        val idle = state.editing == null && !state.picking
        return state.copy(
            capacity = capacity,
            analysis = analysis,
            canWrite = state.availability == NfcAvailability.ENABLED && idle && analysis.block == null,
            canSave = idle && state.actions.isNotEmpty() && analysis.invalid.isEmpty(),
        )
    }

    private fun analyse(draft: TaskDraft, capacity: Int) = TaskMessageBuilder.analyse(draft, appPackage, capacity)
}
