package com.tagforge.app.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.app.feature.write.OverwritePrompt
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistorySummarizer
import com.tagforge.domain.history.RecordHistoryUseCase
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.scan.ScanState
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tools.CloneReport
import com.tagforge.domain.tools.CloneSource
import com.tagforge.domain.tools.CloneTagUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The two-tap clone as a state machine: scan the source (read only), look at what will be copied, then scan the
 * destination (G5: a non-empty destination asks before anything is overwritten), write and verify.
 *
 * The captured source survives the screen being hidden; discarding it takes an explicit back or reset.
 */
class CloneViewModel(
    private val coordinator: ScanCoordinator,
    private val recordHistory: RecordHistoryUseCase,
    availability: Flow<NfcAvailability>,
) : ViewModel() {

    private val clone = CloneTagUseCase()

    private val _state = MutableStateFlow(CloneUiState())
    val uiState: StateFlow<CloneUiState> = _state.asStateFlow()

    private val eventChannel = Channel<CloneEvent>(Channel.BUFFERED)
    val events: Flow<CloneEvent> = eventChannel.receiveAsFlow()

    private var armJob: Job? = null
    private var captured: CloneSource? = null
    private var armedOperation: TagOperation<*>? = null
    private var pendingDecision: CompletableDeferred<Boolean>? = null

    init {
        viewModelScope.launch { availability.collect { value -> _state.update { it.copy(availability = value) } } }
    }

    // ---- step 1 ------------------------------------------------------------------------------------------------------

    fun onCopyUserPagesChanged(enabled: Boolean) {
        if (_state.value.step == CloneStep.SOURCE) _state.update { it.copy(copyUserPages = enabled) }
    }

    fun onStart() {
        val state = _state.value
        if (!state.canStart || state.step != CloneStep.SOURCE || state.sheet != null) return
        armSource()
    }

    // ---- step 2 ------------------------------------------------------------------------------------------------------

    fun onScanTarget() {
        val state = _state.value
        if (!state.canStart || state.step != CloneStep.TARGET || captured == null) return
        armTarget()
    }

    fun onOverwriteDecision(confirmed: Boolean) {
        pendingDecision?.complete(confirmed)
    }

    fun onRetry() {
        val failure = _state.value.sheet as? CloneSheet.Failure ?: return
        if (failure.step == CloneStep.SOURCE) armSource() else armTarget()
    }

    // ---- leaving -----------------------------------------------------------------------------------------------------

    /** Closing the sheet. While the overwrite question is open it means "no"; while a tag is being written it is ignored. */
    fun onSheetDismissed() {
        val state = _state.value
        if (state.overwrite != null) {
            pendingDecision?.complete(false)
            return
        }
        if (state.sheet is CloneSheet.Working) return
        if (state.sheet is CloneSheet.Success) {
            onReset()
            return
        }
        armJob?.cancel()
        disarm()
        _state.update { it.copy(sheet = null) }
    }

    /** Throws the captured source away and starts over. */
    fun onReset() {
        armJob?.cancel()
        pendingDecision?.complete(false)
        disarm()
        captured = null
        _state.update { it.copy(step = CloneStep.SOURCE, source = null, sheet = null, overwrite = null) }
    }

    /** Returns true when the back press was consumed (step 2 goes back to step 1). */
    fun onBack(): Boolean {
        val state = _state.value
        if (state.sheet is CloneSheet.Working) return true   // never abandon a tag half written
        if (state.step != CloneStep.TARGET) return false
        onReset()
        return true
    }

    /** The screen left the foreground: stop waiting for a tag, keep the captured source, never abort a running write. */
    fun onHidden() {
        if (_state.value.sheet is CloneSheet.Working) return
        armJob?.cancel()
        pendingDecision?.complete(false)
        disarm()
        _state.update { it.copy(sheet = null, overwrite = null) }
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

    // ---- arming ------------------------------------------------------------------------------------------------------

    private fun armSource() {
        armJob?.cancel()
        val operation = clone.readSource(includeUserPages = _state.value.copyUserPages)
        armedOperation = operation
        val scan = coordinator.arm(operation)
        _state.update { it.copy(sheet = CloneSheet.Waiting(CloneStep.SOURCE)) }

        var detected = false
        armJob = viewModelScope.launch {
            scan.collect { scanState ->
                if (scanState is ScanState.Working && !detected) {
                    detected = true
                    eventChannel.trySend(CloneEvent.TagDetected)
                }
                when (scanState) {
                    ScanState.Idle, is ScanState.Waiting -> Unit
                    is ScanState.Working -> _state.update { it.copy(sheet = CloneSheet.Working(CloneStep.SOURCE, scanState.phase)) }
                    is ScanState.Success -> onSourceCaptured(scanState.value)
                    is ScanState.Failure -> onFailure(CloneStep.SOURCE, scanState.error)
                }
            }
        }
    }

    private fun onSourceCaptured(source: CloneSource) {
        captured = source
        val summary = CloneSourceSummary(
            chip = source.info.chipName,
            uid = source.info.uid,
            messageSize = DefaultNdefCodec.encode(source.message).size,
            summary = HistorySummarizer.summarize(source.message),
            withUserPages = source.userPages != null,
            classicBlocks = source.classic?.dataBlocks?.size,
        )
        _state.update { it.copy(step = CloneStep.TARGET, source = summary, sheet = null) }
        eventChannel.trySend(CloneEvent.SourceCaptured)
    }

    private fun armTarget() {
        val source = captured ?: return
        armJob?.cancel()
        pendingDecision?.complete(false)
        val operation = clone.writeTarget(source) { existing -> askToOverwrite(existing) }
        armedOperation = operation
        val scan = coordinator.arm(operation)
        _state.update { it.copy(sheet = CloneSheet.Waiting(CloneStep.TARGET), overwrite = null) }

        var detected = false
        armJob = viewModelScope.launch {
            scan.collect { scanState ->
                if (scanState is ScanState.Working && !detected) {
                    detected = true
                    eventChannel.trySend(CloneEvent.TagDetected)
                }
                when (scanState) {
                    ScanState.Idle, is ScanState.Waiting -> Unit
                    is ScanState.Working -> _state.update { it.copy(sheet = CloneSheet.Working(CloneStep.TARGET, scanState.phase)) }
                    is ScanState.Success -> onCloned(scanState.value, source.message)
                    is ScanState.Failure -> {
                        onFailure(CloneStep.TARGET, scanState.error)
                        if (scanState.error != TagError.Cancelled && scanState.error != TagError.SameTag) {
                            recordHistory.record(HistoryKind.CLONE, scanState.tag, source.message, scanState.error)
                        }
                    }
                }
            }
        }
    }

    /** Runs inside the clone operation: suspends until the user answers the dialog. */
    private suspend fun askToOverwrite(existing: NdefMessage?): Boolean {
        val decision = CompletableDeferred<Boolean>()
        pendingDecision = decision
        val summary = existing?.let(HistorySummarizer::summarize)?.ifEmpty { null }
        _state.update { it.copy(overwrite = OverwritePrompt(summary)) }
        return try {
            decision.await()
        } finally {
            if (pendingDecision === decision) pendingDecision = null
            _state.update { it.copy(overwrite = null) }
        }
    }

    private suspend fun onCloned(report: CloneReport, message: NdefMessage) {
        _state.update { it.copy(sheet = CloneSheet.Success(report)) }
        eventChannel.trySend(CloneEvent.Succeeded)
        recordHistory.record(HistoryKind.CLONE, report.target, message, error = null)
    }

    private fun onFailure(step: CloneStep, error: TagError) {
        if (error == TagError.Cancelled) {
            _state.update { it.copy(sheet = null, overwrite = null) }
            return
        }
        _state.update { it.copy(sheet = CloneSheet.Failure(step, error, canRetry = error !is TagError.Unsupported)) }
        eventChannel.trySend(CloneEvent.Failed)
    }
}
