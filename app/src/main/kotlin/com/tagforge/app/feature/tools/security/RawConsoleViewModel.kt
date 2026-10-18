package com.tagforge.app.feature.tools.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.scan.ScanState
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.security.ConfirmedPlan
import com.tagforge.domain.security.RawClass
import com.tagforge.domain.security.RawCommand
import com.tagforge.domain.security.RawCommandGuard
import com.tagforge.domain.security.RawConsoleProbeUseCase
import com.tagforge.domain.security.RawConsoleUseCase
import com.tagforge.domain.security.RawProbeResult
import com.tagforge.domain.security.RawRefusal
import com.tagforge.domain.security.RawResponse
import com.tagforge.domain.security.SecurityFailure
import com.tagforge.domain.settings.AppSettings
import com.tagforge.domain.tag.TagError
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
 * The raw command console (NS-08). Advanced mode only, with a warning acknowledged once per session. A frame goes
 * through two taps unless it is read class: the first tap looks at the tag and classifies the frame, the second sends it
 * after the confirmation that frame needs (a click for user pages, the typed UID for reserved pages).
 */
class RawConsoleViewModel(
    private val coordinator: ScanCoordinator,
    availability: Flow<NfcAvailability>,
    settings: Flow<AppSettings>,
    /** Shared with the app container so the warning is shown once per process, not once per screen. */
    private val acknowledged: MutableStateFlow<Boolean>,
) : ViewModel() {

    private val _state = MutableStateFlow(RawConsoleUiState(acknowledged = acknowledged.value))
    val uiState: StateFlow<RawConsoleUiState> = _state.asStateFlow()

    private val eventChannel = Channel<ConsoleEvent>(Channel.BUFFERED)
    val events: Flow<ConsoleEvent> = eventChannel.receiveAsFlow()

    private var armJob: Job? = null
    private var armedOperation: TagOperation<*>? = null

    init {
        viewModelScope.launch { availability.collect { value -> _state.update { it.copy(availability = value) } } }
        viewModelScope.launch { settings.collect { value -> _state.update { it.copy(advanced = value.advancedMode) } } }
        viewModelScope.launch { acknowledged.collect { value -> _state.update { it.copy(acknowledged = value) } } }
    }

    fun onHexChange(text: String) {
        _state.update { it.copy(hexInput = text, hexError = false) }
    }

    fun onAcknowledge() {
        acknowledged.value = true
    }

    /** First tap: validate the text, then let the tag and the guard decide what happens next. */
    fun onSend() {
        val state = _state.value
        if (state.needsAck || !state.canSend) return
        val frame = RawCommandGuard.parseHex(state.hexInput)
        if (frame == null) {
            _state.update { it.copy(hexError = true) }
            return
        }
        run(SecurityStage.READ, RawConsoleProbeUseCase(frame, state.advanced), frame) { result -> onProbed(frame, result) }
    }

    private fun onProbed(frame: Bytes, result: RawProbeResult) {
        when (result) {
            is RawProbeResult.Sent -> finish(frame, ConsoleOutcome.Reply(result.response.reply))
            is RawProbeResult.Refused -> finish(frame, ConsoleOutcome.Failed(refusalError(result.reason)))
            is RawProbeResult.NeedsConfirmation -> {
                val pending = when {
                    result.kind == RawClass.UserWrite -> ConsolePending.Light(frame, result.state?.model)
                    result.state == null -> ConsolePending.Unplannable(frame)
                    else -> when (val plan = RawConsoleUseCase.plan(frame, result.state!!)) {
                        is Outcome.Ok -> ConsolePending.Typed(frame, plan.value)
                        is Outcome.Err -> ConsolePending.Unplannable(frame)
                    }
                }
                _state.update { it.copy(sheet = null, pending = pending) }
            }
        }
    }

    // ---- the second tap -----------------------------------------------------------------------------------------------

    fun onLightConfirm() {
        val pending = _state.value.pending as? ConsolePending.Light ?: return
        val authorized = RawCommandGuard.authorize(pending.frame, pending.model, null, advanced = _state.value.advanced, lightweightConfirmed = true)
        (authorized as? Outcome.Ok)?.let { execute(it.value) }
    }

    fun onTypedChange(text: String) {
        _state.update { state ->
            val pending = state.pending as? ConsolePending.Typed ?: return@update state
            state.copy(pending = pending.copy(typed = text))
        }
    }

    fun onTypedConfirm() {
        val state = _state.value
        val pending = state.pending as? ConsolePending.Typed ?: return
        if (!state.canConfirmTyped) return
        val confirmed: ConfirmedPlan = pending.plan.confirm(pending.typed) ?: return
        val authorized = RawCommandGuard.authorize(
            pending.frame, pending.plan.model, null, advanced = state.advanced, plan = confirmed,
        )
        (authorized as? Outcome.Ok)?.let { execute(it.value) }
    }

    fun onCancelPending() {
        _state.update { it.copy(pending = null) }
    }

    private fun execute(command: RawCommand) {
        val frame = command.frame
        _state.update { it.copy(pending = null) }
        run(SecurityStage.EXECUTE, RawConsoleUseCase(command), frame) { response: RawResponse -> finish(frame, ConsoleOutcome.Reply(response.reply)) }
    }

    // ---- the sheet -------------------------------------------------------------------------------------------------------

    fun onSheetDismissed() {
        if (_state.value.sheet is SecuritySheet.Working) return
        armJob?.cancel()
        disarm()
        _state.update { it.copy(sheet = null) }
    }

    /** The screen left the foreground: stop waiting, drop anything unconfirmed, but never abort a command in flight. */
    fun onHidden() {
        if (_state.value.sheet is SecuritySheet.Working) return
        armJob?.cancel()
        disarm()
        _state.update { it.copy(sheet = null, pending = null) }
    }

    override fun onCleared() {
        disarm()
    }

    private fun disarm() {
        armedOperation?.let(coordinator::disarmIfCurrent)
        armedOperation = null
    }

    private fun finish(frame: Bytes, outcome: ConsoleOutcome) {
        _state.update { it.copy(sheet = null, log = it.log + ConsoleEntry(frame.toHex(" "), outcome)) }
        eventChannel.trySend(if (outcome is ConsoleOutcome.Failed || outcome == ConsoleOutcome.TagLost) ConsoleEvent.Failed else ConsoleEvent.Succeeded)
    }

    private fun <T> run(stage: SecurityStage, operation: TagOperation<T>, frame: Bytes, onSuccess: (T) -> Unit) {
        armJob?.cancel()
        armedOperation = operation
        val scan = coordinator.arm(operation)
        _state.update { it.copy(sheet = SecuritySheet.Waiting(stage)) }

        var detected = false
        armJob = viewModelScope.launch {
            scan.collect { scanState ->
                when (scanState) {
                    ScanState.Idle, is ScanState.Waiting -> Unit
                    is ScanState.Working -> {
                        if (!detected) {
                            detected = true
                            eventChannel.trySend(ConsoleEvent.TagDetected)
                        }
                        _state.update { it.copy(sheet = SecuritySheet.Working(stage, scanState.phase)) }
                    }
                    is ScanState.Success -> onSuccess(scanState.value)
                    is ScanState.Failure -> when (scanState.error) {
                        TagError.Cancelled -> _state.update { it.copy(sheet = null) }
                        TagError.TagLost -> finish(frame, ConsoleOutcome.TagLost)
                        else -> finish(frame, ConsoleOutcome.Failed(scanState.error))
                    }
                }
            }
        }
    }

    private fun refusalError(reason: RawRefusal): TagError = when (reason) {
        RawRefusal.AdvancedRequired -> TagError.Security(SecurityFailure.AdvancedRequired)
        is RawRefusal.Invalid -> TagError.Malformed(reason.reason.name)
        RawRefusal.NeedsConfirmation, RawRefusal.NeedsTypedConfirmation, RawRefusal.NotReserved -> TagError.Cancelled
    }
}
