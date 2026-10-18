package com.tagforge.app.feature.tools.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.history.RecordHistoryUseCase
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.scan.ScanState
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.security.ConfirmedPlan
import com.tagforge.domain.security.Password
import com.tagforge.domain.security.SecurityFailure
import com.tagforge.domain.security.SecurityState
import com.tagforge.domain.security.SecuritySummary
import com.tagforge.domain.settings.AppSettings
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * NS-01 as a state machine: form -> read the tag -> dry-run -> typed UID -> second tap that executes. Nothing is armed to
 * WRITE until the typed text matches the UID (and the irreversible effects were acknowledged); cancel, back, dismissing the
 * sheet or leaving the screen at any earlier point means zero writes. Secrets are wiped after a successful run.
 */
class SecurityViewModel(
    private val coordinator: ScanCoordinator,
    private val recordHistory: RecordHistoryUseCase,
    availability: Flow<NfcAvailability>,
    settings: Flow<AppSettings>,
    internal val random: Random = SecureRandom().asKotlinRandom(),
) : ViewModel() {

    private val _state = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _state.asStateFlow()

    private val eventChannel = Channel<SecurityEvent>(Channel.BUFFERED)
    val events: Flow<SecurityEvent> = eventChannel.receiveAsFlow()

    private var armJob: Job? = null
    private var armedOperation: TagOperation<*>? = null
    private var confirmed: ConfirmedPlan? = null
    private var rerun: (() -> Unit)? = null

    init {
        viewModelScope.launch { availability.collect { value -> _state.update { it.copy(availability = value) } } }
        viewModelScope.launch { settings.collect { value -> _state.update { it.copy(advanced = value.advancedMode) } } }
    }

    // ---- the form ------------------------------------------------------------------------------------------------

    fun onChoose(action: SecurityAction) {
        wipe()
        _state.update { SecurityUiState(availability = it.availability, advanced = it.advanced, action = action, step = SecurityStep.FORM) }
        if (action == SecurityAction.UNLOCK || action == SecurityAction.REMOVE_PASSWORD) armLimitProbe()
    }

    fun onFormChange(transform: (SecurityForm) -> SecurityForm) {
        _state.update { it.copy(form = transform(it.form), problem = null, failure = null) }
    }

    fun onGeneratePassword() {
        val password = Password.random(random).hex
        val pack = Password.randomPack(random).toHex()
        onFormChange { it.copy(passwordHex = password, packHex = pack, storedAck = false) }
    }

    /** Reads the tag's attempt limit (nothing is sent but reads) so a password may be typed (W2). */
    fun onProbeLimit() {
        val state = _state.value
        if (state.action == null || !state.canStart || state.sheet != null) return
        armLimitProbe()
    }

    fun onLimitUnderstood() {
        _state.update { if (it.authLimit == null) it else it.copy(limitUnderstood = true) }
    }

    fun onTypedChange(text: String) {
        _state.update { it.copy(typed = text) }
    }

    fun onUnderstoodChange(value: Boolean) {
        _state.update { it.copy(understood = value) }
    }

    /** "Next": validate, then read the tag (or, for Unlock, send the one authentication). Nothing is written. */
    fun onNext() {
        val state = _state.value
        val action = state.action ?: return
        if (!state.canStart || state.sheet != null) return
        if (!state.canGoNext) return
        val problem = SecurityPlans.validate(action, state.form)
        if (problem != null) {
            _state.update { it.copy(problem = problem) }
            return
        }
        if (action == SecurityAction.UNLOCK) armUnlock() else armRead(action)
    }

    // ---- the dry-run -----------------------------------------------------------------------------------------------

    /** The typed UID matched and the effects were acknowledged: only now is a write armed. */
    fun onConfirm() {
        val state = _state.value
        val plan = state.plan ?: return
        val action = state.action ?: return
        if (!state.canConfirm || state.sheet != null) return
        confirmed = plan.confirm(state.typed) ?: return
        armExecute(action)
    }

    /** Returns true when the back press was consumed. */
    fun onBack(): Boolean {
        val state = _state.value
        if (state.sheet != null && state.sheet !is SecuritySheet.Working) {
            onSheetDismissed()
            return true
        }
        return when (state.step) {
            SecurityStep.DRY_RUN -> {
                confirmed = null
                _state.update { it.copy(step = SecurityStep.FORM, plan = null, typed = "", understood = false, failure = null) }
                true
            }
            SecurityStep.FORM -> {
                wipe()
                _state.update { SecurityUiState(availability = it.availability, advanced = it.advanced) }
                true
            }
            SecurityStep.CHOOSE -> false
        }
    }

    // ---- the sheet -------------------------------------------------------------------------------------------------

    fun onRetry() {
        rerun?.invoke()
    }

    fun onSheetDismissed() {
        val sheet = _state.value.sheet
        if (sheet is SecuritySheet.Working) return
        armJob?.cancel()
        disarm()
        _state.update { it.copy(sheet = null) }
    }

    /** The screen left the foreground: stop waiting for a tag, but never abort a tag that is being changed. */
    fun onHidden() {
        if (_state.value.sheet is SecuritySheet.Working) return
        armJob?.cancel()
        disarm()
        _state.update { it.copy(sheet = null) }
    }

    override fun onCleared() {
        disarm()
    }

    private fun disarm() {
        armedOperation?.let(coordinator::disarmIfCurrent)
        armedOperation = null
    }

    /** Forget every secret this screen holds. */
    private fun wipe() {
        confirmed = null
        rerun = null
    }

    // ---- arming ----------------------------------------------------------------------------------------------------

    private fun armRead(action: SecurityAction) {
        val form = _state.value.form
        run(SecurityStage.READ, SecurityPlans.readOperation(form)) { scan -> onTagRead(action, form, scan.value) }
        rerun = { armRead(action) }
    }

    private fun onTagRead(action: SecurityAction, form: SecurityForm, tagState: SecurityState) {
        val advanced = _state.value.advanced
        when (val planned = SecurityPlans.plan(action, form, tagState, advanced)) {
            is Outcome.Err -> _state.update { it.copy(sheet = null, failure = planned.error, step = SecurityStep.FORM) }
            is Outcome.Ok -> _state.update {
                it.copy(sheet = null, failure = null, plan = planned.value, typed = "", understood = false, step = SecurityStep.DRY_RUN)
            }
        }
    }

    private fun armExecute(action: SecurityAction) {
        val plan = confirmed ?: return
        val operation = SecurityPlans.execution(action, plan)
        rerun = { armExecute(action) }
        runExecution(operation, action, plan)
    }

    private fun armLimitProbe() {
        run(SecurityStage.READ, SecurityPlans.limitProbe()) { scan ->
            _state.update { it.copy(sheet = null, authLimit = scan.value) }
        }
        rerun = { armLimitProbe() }
    }

    private fun armUnlock() {
        val form = _state.value.form
        run(SecurityStage.EXECUTE, SecurityPlans.authenticate(form)) { scan ->
            val report = scan.value
            _state.update { it.copy(sheet = SecuritySheet.Success(SecurityAction.UNLOCK, report.pack), form = SecurityForm()) }
            eventChannel.trySend(SecurityEvent.Succeeded)
        }
        rerun = { onNext() }
    }

    // ---- running ---------------------------------------------------------------------------------------------------

    private fun <T> runExecution(operation: TagOperation<T>, action: SecurityAction, plan: ConfirmedPlan) {
        run(SecurityStage.EXECUTE, operation, onFailure = { scan -> recordFailure(plan, scan.tag, scan.error) }) { scan ->
            recordHistory.recordSecurity(scan.tag, SecuritySummary.of(plan.plan.operation, null), null)
            wipe()
            _state.update {
                SecurityUiState(
                    availability = it.availability,
                    advanced = it.advanced,
                    sheet = SecuritySheet.Success(action),
                )
            }
            eventChannel.trySend(SecurityEvent.Succeeded)
        }
    }

    private suspend fun recordFailure(plan: ConfirmedPlan, tag: TagInfo?, error: TagError) {
        recordHistory.recordSecurity(tag, SecuritySummary.of(plan.plan.operation, error), error)
    }

    private fun <T> run(
        stage: SecurityStage,
        operation: TagOperation<T>,
        onFailure: suspend (ScanState.Failure) -> Unit = {},
        onSuccess: suspend (ScanState.Success<T>) -> Unit,
    ) {
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
                            eventChannel.trySend(SecurityEvent.TagDetected)
                        }
                        _state.update { it.copy(sheet = SecuritySheet.Working(stage, scanState.phase)) }
                    }
                    is ScanState.Success -> onSuccess(scanState)
                    is ScanState.Failure -> {
                        if (scanState.error == TagError.Cancelled) {
                            _state.update { it.copy(sheet = null) }
                        } else {
                            onFailure(scanState)
                            _state.update { it.copy(sheet = SecuritySheet.Failure(scanState.error, canRetry = isRetryable(scanState.error))) }
                            eventChannel.trySend(SecurityEvent.Failed)
                        }
                    }
                }
            }
        }
    }

    /** A vanished tag or a different tag is worth another tap; a wrong password or a locked area is not. */
    private fun isRetryable(error: TagError): Boolean = when (error) {
        TagError.TagLost, TagError.Timeout, is TagError.Io -> true
        is TagError.Security -> error.failure is SecurityFailure.Partial || error.failure == SecurityFailure.UidMismatch
        else -> false
    }
}
