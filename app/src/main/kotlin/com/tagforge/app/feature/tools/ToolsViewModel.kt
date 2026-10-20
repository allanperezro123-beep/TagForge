package com.tagforge.app.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.RecordHistoryUseCase
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.scan.ScanState
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.settings.AppSettings
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tools.EraseTagUseCase
import com.tagforge.domain.tools.FormatTagUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the Erase and Format tools: the DESTRUCTIVE protocol (G5) as a state machine.
 *
 *   request -> confirmation dialog (nothing armed) -> confirm -> armed sheet (waiting) -> tag tapped -> working -> result
 *
 * Cancel, back, dismissing the dialog or leaving the screen before the confirmation is accepted never arms anything,
 * so no tag can be touched. Once a tag is being changed the sheet cannot be dismissed: aborting half way is exactly
 * what the use cases' write ordering exists to survive, but there is no reason to provoke it.
 */
class ToolsViewModel(
    private val coordinator: ScanCoordinator,
    private val recordHistory: RecordHistoryUseCase,
    availability: Flow<NfcAvailability>,
    settings: Flow<AppSettings> = flowOf(AppSettings()),
) : ViewModel() {

    private val _state = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _state.asStateFlow()

    private val eventChannel = Channel<ToolsEvent>(Channel.BUFFERED)
    val events: Flow<ToolsEvent> = eventChannel.receiveAsFlow()

    private var armJob: Job? = null
    private var lastTool: DestructiveTool? = null
    private var armedOperation: TagOperation<*>? = null

    /** The Settings switch "ask before erasing or formatting". Asking is the default until the setting says otherwise. */
    private var askFirst = true

    init {
        viewModelScope.launch { availability.collect { value -> _state.update { it.copy(availability = value) } } }
        viewModelScope.launch { settings.collect { value -> askFirst = value.confirmEraseFormat } }
    }

    // ---- the question ------------------------------------------------------------------------------------------------

    fun onRequest(tool: DestructiveTool) {
        if (!_state.value.canStart || _state.value.sheet != null) return
        if (!askFirst) {
            // The user turned the question off in Settings: the tap that arms the tool is the only step left.
            lastTool = tool
            arm(tool)
            return
        }
        _state.update { it.copy(confirm = tool) }
    }

    fun onCancelConfirm() {
        _state.update { it.copy(confirm = null) }
    }

    /** Returns true when the back press was consumed by the open dialog. */
    fun onBack(): Boolean {
        if (_state.value.confirm == null) return false
        onCancelConfirm()
        return true
    }

    fun onConfirm() {
        val tool = _state.value.confirm ?: return
        _state.update { it.copy(confirm = null) }
        lastTool = tool
        arm(tool)
    }

    // ---- the sheet ---------------------------------------------------------------------------------------------------

    /** The user pressed "try again" after a failure; they already confirmed, so no second question. */
    fun onRetry() {
        lastTool?.let(::arm)
    }

    fun onSheetDismissed() {
        if (_state.value.sheet is ToolSheet.Working) return
        armJob?.cancel()
        disarm()
        _state.update { it.copy(sheet = null) }
    }

    /** The screen left the foreground: stop waiting for a tag, but never abort a tag that is being changed. */
    fun onHidden() {
        if (_state.value.sheet is ToolSheet.Working) return
        armJob?.cancel()
        disarm()
        _state.update { it.copy(confirm = null, sheet = null) }
    }

    override fun onCleared() {
        disarm()
    }

    /** Only our own operation: the screen we navigate to may already have armed its own. */
    private fun disarm() {
        armedOperation?.let(coordinator::disarmIfCurrent)
        armedOperation = null
    }

    // ---- arming ------------------------------------------------------------------------------------------------------

    private fun arm(tool: DestructiveTool) {
        when (tool) {
            DestructiveTool.ERASE -> start(tool, EraseTagUseCase(), HistoryKind.ERASE) { it.hadContent }
            DestructiveTool.FORMAT -> start(tool, FormatTagUseCase(), HistoryKind.FORMAT) { it.changed }
        }
    }

    private fun <T> start(tool: DestructiveTool, operation: TagOperation<T>, kind: HistoryKind, changed: (T) -> Boolean) {
        armJob?.cancel()
        armedOperation = operation
        val scan = coordinator.arm(operation)
        _state.update { it.copy(sheet = ToolSheet.Waiting(tool)) }

        var detected = false
        armJob = viewModelScope.launch {
            scan.collect { scanState ->
                if (scanState is ScanState.Working && !detected) {
                    detected = true
                    eventChannel.trySend(ToolsEvent.TagDetected)
                }
                handle(tool, kind, scanState, changed)
            }
        }
    }

    private suspend fun <T> handle(tool: DestructiveTool, kind: HistoryKind, scan: ScanState<T>, changed: (T) -> Boolean) {
        when (scan) {
            ScanState.Idle, is ScanState.Waiting -> Unit
            is ScanState.Working -> _state.update { it.copy(sheet = ToolSheet.Working(tool, scan.phase)) }
            is ScanState.Success -> {
                _state.update { it.copy(sheet = ToolSheet.Success(tool, changed(scan.value))) }
                eventChannel.trySend(ToolsEvent.Succeeded)
                recordHistory.record(kind, scan.tag, message = null, error = null)
            }
            is ScanState.Failure -> {
                if (scan.error == TagError.Cancelled) {
                    _state.update { it.copy(sheet = null) }
                    return
                }
                _state.update { it.copy(sheet = ToolSheet.Failure(tool, scan.error, canRetry = isRetryable(scan.error))) }
                eventChannel.trySend(ToolsEvent.Failed)
                recordHistory.record(kind, scan.tag, message = null, error = scan.error)
            }
        }
    }

    /** A locked tag or an unsupported card fails the same way every time. */
    private fun isRetryable(error: TagError): Boolean = error != TagError.ReadOnly && error !is TagError.Unsupported
}
