package com.tagforge.app.feature.write

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.history.HistorySummarizer
import com.tagforge.domain.history.RecordHistoryUseCase
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefBudget
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.RecordInterpreter
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.RecordSpecEncoder
import com.tagforge.domain.ndef.spec.SpecField
import com.tagforge.domain.ports.SettingsStore
import com.tagforge.domain.ports.TemplateRepository
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.scan.ScanState
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.template.Template
import com.tagforge.domain.template.TemplateNames
import com.tagforge.domain.write.WriteDraft
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
 * State holder for the Write tab: the wizard (type -> form -> review), the live size budget, and the armed write
 * operation with its confirmation, success and failure states.
 *
 * Pure orchestration: validation, encoding and sizing live in the domain ([WriteDraft], `RecordSpecValidator`), the
 * write itself in [WriteNdefUseCase].
 */
class WriteViewModel(
    private val coordinator: ScanCoordinator,
    private val settings: SettingsStore,
    private val recordHistory: RecordHistoryUseCase,
    private val templates: TemplateRepository,
    private val clock: Clock,
    private val appPackage: String,
    availability: Flow<NfcAvailability>,
    initialTemplateId: Long? = null,
    /** Records decoded from a QR code; opens the wizard at the review step so they can be edited before writing. */
    initialRecords: List<RecordSpec> = emptyList(),
    /** A pinned-template shortcut: once the template is loaded, wait for a tag straight away if it can be written. */
    armOnOpen: Boolean = false,
) : ViewModel() {

    private val _state = MutableStateFlow(derive(WriteUiState(analysis = WriteDraft().analyse(0, appPackage))))
    val uiState: StateFlow<WriteUiState> = _state.asStateFlow()

    private val eventChannel = Channel<WriteEvent>(Channel.BUFFERED)
    val events: Flow<WriteEvent> = eventChannel.receiveAsFlow()

    private var armJob: Job? = null
    private var armedOperation: com.tagforge.domain.scan.TagOperation<*>? = null
    private var pendingDecision: CompletableDeferred<Boolean>? = null
    private var lastMessage: NdefMessage? = null
    private var removed: Pair<Int, RecordSpec>? = null

    init {
        viewModelScope.launch { availability.collect { value -> mutate { it.copy(availability = value) } } }
        viewModelScope.launch {
            val remembered = settings.settings.first().lastTargetModel
            mutate { if (it.target is WriteTarget.Chip) it.copy(target = WriteTarget.Chip(remembered)) else it }
        }
        if (initialRecords.isNotEmpty()) mutate { it.copy(records = initialRecords, step = WriteStep.REVIEW) }
        if (initialTemplateId != null) {
            viewModelScope.launch {
                val template = templates.get(initialTemplateId) ?: return@launch
                if (template.records.isNotEmpty()) mutate { it.copy(records = template.records, step = WriteStep.REVIEW) }
                if (armOnOpen) onWrite()
            }
        }
    }

    // ---- wizard ---------------------------------------------------------------------------------------------

    fun onTypeSelected(kind: RecordKind) = mutate {
        it.copy(step = WriteStep.FORM, editing = EditingRecord(index = null, spec = kind.newSpec()), trail = it.trailWithCurrent())
    }

    fun onDraftChanged(spec: RecordSpec) = mutate { state -> state.editing?.let { state.copy(editing = it.copy(spec = spec)) } ?: state }

    fun onFieldTouched(field: SpecField) = mutate { state ->
        state.editing?.let { state.copy(editing = it.copy(touched = it.touched + field)) } ?: state
    }

    fun onContinue() = mutate { state ->
        val editing = state.editing ?: return@mutate state
        if (!editing.isValid) return@mutate state.copy(editing = editing.copy(showAllErrors = true))

        removed = null
        val index = editing.index
        val records = if (index == null) state.records + editing.spec else state.records.toMutableList().also { it[index] = editing.spec }
        state.copy(records = records, editing = null, step = WriteStep.REVIEW, canUndoRemove = false, trail = state.trailWithCurrent())
    }

    fun onAddRecord() = mutate { if (it.step == WriteStep.TYPE) it.copy(editing = null) else it.copy(step = WriteStep.TYPE, editing = null, trail = it.trailWithCurrent()) }

    fun onEditRecord(index: Int) = mutate { state ->
        val spec = state.records.getOrNull(index) ?: return@mutate state
        state.copy(step = WriteStep.FORM, editing = EditingRecord(index, spec), trail = state.trailWithCurrent())
    }

    fun onRemoveRecord(index: Int) {
        mutate { state ->
            val spec = state.records.getOrNull(index) ?: return@mutate state
            removed = index to spec
            val draft = WriteDraft(state.records).remove(index)
            // The list changed under the saved steps: going back must not bring the removed record to life.
            state.copy(
                records = draft.records,
                step = if (draft.records.isEmpty()) WriteStep.TYPE else state.step,
                canUndoRemove = true,
                trail = emptyList(),
            )
        }
        if (removed != null) eventChannel.trySend(WriteEvent.RecordRemoved)
    }

    fun onUndoRemove() = mutate { state ->
        val (index, spec) = removed ?: return@mutate state
        removed = null
        val records = state.records.toMutableList().also { it.add(index.coerceAtMost(it.size), spec) }
        state.copy(records = records, step = WriteStep.REVIEW, canUndoRemove = false, trail = emptyList())
    }

    fun onMoveRecord(index: Int, delta: Int) = mutate { it.copy(records = WriteDraft(it.records).move(index, delta).records, trail = emptyList()) }

    fun onIncludeAarChanged(include: Boolean) = mutate { it.copy(includeAar = include) }

    fun onTargetSelected(target: WriteTarget) {
        mutate { it.copy(target = target) }
        if (target is WriteTarget.Chip) {
            viewModelScope.launch { settings.update { it.copy(lastTargetModel = target.model) } }
        }
    }

    /**
     * Steps back to where the user was before the last forward move (review -> the filled form -> the picker). Returns true
     * when the wizard consumed it; false means there is no earlier step, so the caller leaves to the previous section.
     */
    fun onBack(): Boolean {
        val previous = _state.value.trail.lastOrNull() ?: return false
        mutate { it.copy(step = previous.step, editing = previous.editing, records = previous.records, trail = it.trail.dropLast(1)) }
        return true
    }

    // ---- templates ---------------------------------------------------------------------------------------------

    fun onSaveTemplate(requestedName: String) {
        val records = _state.value.records
        if (records.isEmpty()) return
        viewModelScope.launch {
            val existing = templates.observeAll().first().map { it.name }
            val name = TemplateNames.resolve(requestedName, existing) ?: return@launch
            val now = clock.now()
            templates.upsert(Template(name = name, records = records, createdAt = now, updatedAt = now))
            eventChannel.trySend(WriteEvent.TemplateSaved(name))
        }
    }

    // ---- writing -----------------------------------------------------------------------------------------------

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

    fun onWriteAnother() = onRetry()

    fun onOverwriteDecision(confirmed: Boolean) {
        pendingDecision?.complete(confirmed)
    }

    /**
     * Closing the sheet. While the question "replace existing content?" is open it means "no". While a tag is being
     * written it is ignored: aborting mid-write is exactly what the write ordering protects against.
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

    /** The tab left the screen: stop waiting for a tag, but never abort a write that is already running. */
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

    /** Only our own operation: the screen we navigate to may already have armed its own. */
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
                    eventChannel.trySend(WriteEvent.TagDetected)
                }
                handle(scanState, message)
            }
        }
    }

    /** Runs inside the write operation: suspends until the user answers the dialog. */
    private suspend fun askToOverwrite(existing: NdefMessage?): Boolean {
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
                mutate { it.copy(sheet = WriteSheet.Success(scan.value), lastTagCapacity = scan.tag.ndef?.maxSize ?: it.lastTagCapacity) }
                eventChannel.trySend(WriteEvent.Succeeded)
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
                        sheet = WriteSheet.Failure(error, canRetry = isRetryable(error)),
                        lastTagCapacity = tooSmall?.available ?: state.lastTagCapacity,
                        target = if (tooSmall != null) WriteTarget.LastTag(tooSmall.available) else state.target,
                    )
                }
                eventChannel.trySend(WriteEvent.Failed)
                recordHistory.recordWrite(scan.tag, message, error)
            }
        }
    }

    /** A locked or too-small tag fails the same way every time: offering "try again" would just loop. */
    private fun isRetryable(error: TagError): Boolean = error != TagError.ReadOnly && error !is TagError.CapacityExceeded

    // ---- derived state ---------------------------------------------------------------------------------------------

    private fun mutate(transform: (WriteUiState) -> WriteUiState) {
        _state.update { derive(transform(it)) }
    }

    private fun WriteUiState.trailWithCurrent(): List<WizardSnapshot> = trail + WizardSnapshot(step, editing, records)

    private fun derive(state: WriteUiState): WriteUiState {
        val capacity = when (val target = state.target) {
            is WriteTarget.Chip -> NdefBudget.androidMaxSize(target.model)
            is WriteTarget.LastTag -> target.maxSize
        }
        val editing = state.editing
        val saved = if (editing?.index != null) WriteDraft(state.records, state.includeAar).remove(editing.index) else WriteDraft(state.records, state.includeAar)
        val analysis = saved.analyse(capacity, appPackage, pending = editing?.spec)

        val previewRecord = editing?.let { RecordSpecEncoder.encode(it.spec) }
        val canWrite = state.availability == NfcAvailability.ENABLED && editing == null && analysis.block == null

        return state.copy(
            capacity = capacity,
            analysis = analysis,
            previewParsed = previewRecord?.let(RecordInterpreter::interpret),
            previewHex = previewRecord?.let { DefaultNdefCodec.encode(NdefMessage(listOf(it))).toHex(" ") },
            recordPreviews = state.records.map { spec -> RecordSpecEncoder.encode(spec)?.let(RecordInterpreter::interpret) },
            messageHex = analysis.message?.let { DefaultNdefCodec.encode(it).toHex(" ") },
            suggestedChip = if (analysis.block == com.tagforge.domain.write.WriteBlock.OVER_CAPACITY) NdefBudget.smallestChipFor(analysis.budget.used) else null,
            canWrite = canWrite,
        )
    }
}
