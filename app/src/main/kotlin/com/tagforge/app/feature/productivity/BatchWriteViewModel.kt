package com.tagforge.app.feature.productivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.exchange.BatchPlan
import com.tagforge.domain.exchange.BatchRun
import com.tagforge.domain.exchange.BatchWriteOperation
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistorySummarizer
import com.tagforge.domain.history.RecordHistoryUseCase
import com.tagforge.domain.ndef.NdefBudget
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ports.SettingsStore
import com.tagforge.domain.ports.TemplateRepository
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.scan.ScanState
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError
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

/**
 * State holder for batch write (PR-01): one template written to N tags, a `{n}` counter that moves only after a
 * verified write, and a running screen that re-arms by itself for the next tag.
 *
 * The counter is stored in settings after every change, so a killed process resumes where it stopped. Every attempt,
 * successful or not, is a BATCH history entry. All planning (numbering, size over the whole range, duplicate tags)
 * lives in the domain; this class is orchestration.
 */
class BatchWriteViewModel(
    private val coordinator: ScanCoordinator,
    private val settings: SettingsStore,
    private val recordHistory: RecordHistoryUseCase,
    private val templates: TemplateRepository,
    private val appPackage: String,
    availability: Flow<NfcAvailability>,
) : ViewModel() {

    private val _state = MutableStateFlow(derive(BatchUiState()))
    val uiState: StateFlow<BatchUiState> = _state.asStateFlow()

    private val eventChannel = Channel<BatchEvent>(Channel.BUFFERED)
    val events: Flow<BatchEvent> = eventChannel.receiveAsFlow()

    private var armJob: Job? = null
    private var armedOperation: TagOperation<*>? = null
    private var pendingDecision: CompletableDeferred<Boolean>? = null
    private var startEdited = false

    init {
        viewModelScope.launch { availability.collect { value -> mutate { it.copy(availability = value) } } }
        viewModelScope.launch { templates.observeAll().collect { list -> mutate { it.copy(templates = list) } } }
        viewModelScope.launch {
            val stored = settings.settings.first()
            // Whatever the user typed while the stored value was loading wins.
            mutate {
                it.copy(
                    startText = if (startEdited) it.startText else stored.batchNextCounter.toString(),
                    target = stored.lastTargetModel,
                )
            }
        }
    }

    // ---- setup -----------------------------------------------------------------------------------------------------

    fun onTemplateSelected(id: Long) {
        viewModelScope.launch {
            val template = templates.get(id) ?: return@launch
            mutate { it.copy(selectedTemplateId = id, records = template.records) }
        }
    }

    fun onStartChanged(text: String) {
        startEdited = true
        mutate { it.copy(startText = text) }
    }
    fun onStepChanged(text: String) = mutate { it.copy(stepText = text) }
    fun onPadChanged(text: String) = mutate { it.copy(padText = text) }
    fun onTotalChanged(text: String) = mutate { it.copy(totalText = text) }
    fun onAarChanged(include: Boolean) = mutate { it.copy(includeAar = include) }

    fun onTargetSelected(model: NtagModel) {
        mutate { it.copy(target = model) }
        viewModelScope.launch { settings.update { it.copy(lastTargetModel = model) } }
    }

    // ---- running ---------------------------------------------------------------------------------------------------

    fun onStart() {
        val state = _state.value
        if (!state.canStart) return
        val run = BatchRun(BatchPlan(state.records, state.includeAar, state.settings))
        mutate { it.copy(step = BatchStep.RUNNING, run = run, status = BatchStatus.Waiting, prompt = null) }
        armNext()
    }

    fun onPromptDecision(confirmed: Boolean) {
        pendingDecision?.complete(confirmed)
    }

    /** Gives up on the current number: the counter moves on without a tag having been written. */
    fun onSkip() {
        val run = _state.value.run ?: return
        if (_state.value.step != BatchStep.RUNNING || _state.value.status is BatchStatus.Working) return
        val next = run.recordSkip()
        advance(next, BatchStatus.Waiting)
    }

    fun onStop() {
        stopArming()
        mutate { it.copy(step = BatchStep.DONE, prompt = null) }
    }

    /** Back to setup; the start value is the stored counter, i.e. the number that would have been next. */
    fun onNewBatch() {
        stopArming()
        startEdited = false
        viewModelScope.launch {
            val counter = settings.settings.first().batchNextCounter
            mutate { it.copy(step = BatchStep.SETUP, run = null, status = BatchStatus.Waiting, prompt = null, startText = counter.toString()) }
        }
    }

    /** The screen left: stop waiting for a tag, but never abort a write that is already running. */
    fun onHidden() {
        val state = _state.value
        if (state.step != BatchStep.RUNNING || state.status is BatchStatus.Working) return
        stopArming()
        mutate { it.copy(prompt = null, status = BatchStatus.Waiting) }
    }

    fun onVisible() {
        if (_state.value.step == BatchStep.RUNNING && armJob?.isActive != true) armNext()
    }

    override fun onCleared() {
        pendingDecision?.complete(false)
        armedOperation?.let(coordinator::disarmIfCurrent)
    }

    private fun stopArming() {
        armJob?.cancel()
        armJob = null
        pendingDecision?.complete(false)
        armedOperation?.let(coordinator::disarmIfCurrent)
        armedOperation = null
    }

    /** Arms the write for the next number. Must be the last non-suspending step of a result handler. */
    private fun armNext() {
        val run = _state.value.run ?: return
        val message = run.nextMessage(appPackage)
        if (message == null) {
            stopArming()
            mutate { it.copy(step = BatchStep.DONE) }
            return
        }
        stopArming()
        val operation = BatchWriteOperation(
            message = message,
            alreadyWritten = run::needsOverwriteConfirmation,
            confirm = ::ask,
        )
        armedOperation = operation
        val scan = coordinator.arm(operation)
        var detected = false
        armJob = viewModelScope.launch {
            scan.collect { scanState ->
                if (scanState is ScanState.Working && !detected) {
                    detected = true
                    eventChannel.trySend(BatchEvent.TagDetected)
                }
                handle(scanState, run, message)
            }
        }
    }

    /** Runs inside the write operation: suspends until the user answers. */
    private suspend fun ask(existing: NdefMessage?, duplicate: Boolean): Boolean {
        val decision = CompletableDeferred<Boolean>()
        pendingDecision = decision
        val summary = existing?.let(HistorySummarizer::summarize)?.ifEmpty { null }
        mutate { it.copy(prompt = if (duplicate) BatchPrompt.SameTag else BatchPrompt.Overwrite(summary)) }
        return try {
            decision.await()
        } finally {
            if (pendingDecision === decision) pendingDecision = null
            mutate { it.copy(prompt = null) }
        }
    }

    private suspend fun handle(scan: ScanState<WriteReport>, run: BatchRun, message: NdefMessage) {
        when (scan) {
            ScanState.Idle, is ScanState.Waiting -> Unit
            is ScanState.Working -> mutate { it.copy(status = BatchStatus.Working(scan.phase)) }
            is ScanState.Success -> {
                recordHistory.record(HistoryKind.BATCH, scan.tag, message, error = null)
                eventChannel.trySend(BatchEvent.Succeeded)
                advance(run.recordSuccess(scan.tag.uid), BatchStatus.Written(run.nextLabel))
            }
            is ScanState.Failure -> {
                if (scan.error == TagError.Cancelled) {
                    // The user said no to a rewrite: not an attempt, nothing logged, same number, keep waiting.
                    mutate { it.copy(status = BatchStatus.Waiting) }
                    if (armJob?.isActive == true && _state.value.step == BatchStep.RUNNING) armNext()
                    return
                }
                recordHistory.record(HistoryKind.BATCH, scan.tag, message, scan.error)
                eventChannel.trySend(BatchEvent.Failed)
                mutate { it.copy(run = run.recordFailure(), status = BatchStatus.Failed(run.nextLabel, scan.error)) }
                armNext()
            }
        }
    }

    /** Stores the new run and counter, then waits for the next tag (or finishes the batch). */
    private fun advance(next: BatchRun, status: BatchStatus) {
        mutate { it.copy(run = next, status = status) }
        viewModelScope.launch { settings.update { it.copy(batchNextCounter = next.nextCounter) } }
        armNext()
    }

    // ---- derived state ---------------------------------------------------------------------------------------------

    private fun mutate(transform: (BatchUiState) -> BatchUiState) {
        _state.update { derive(transform(it)) }
    }

    private fun derive(state: BatchUiState): BatchUiState {
        val plan = BatchPlan(state.records, state.includeAar, state.settings)
        val capacity = NdefBudget.androidMaxSize(state.target)
        val analysis = plan.analyse(capacity, appPackage)
        return state.copy(
            capacity = capacity,
            usesCounter = plan.usesCounter,
            analysis = analysis,
            canStart = state.step == BatchStep.SETUP && state.availability == NfcAvailability.ENABLED && analysis.block == null,
        )
    }
}
