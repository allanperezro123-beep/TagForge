package com.tagforge.app.feature.tools.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.history.RecordHistoryUseCase
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.scan.ScanState
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.security.ConfirmedPlan
import com.tagforge.domain.security.DiversifiedPassword
import com.tagforge.domain.security.Password
import com.tagforge.domain.security.PasswordInput
import com.tagforge.domain.security.PasswordMode
import com.tagforge.domain.security.SecurityFailure
import com.tagforge.domain.security.SecurityState
import com.tagforge.domain.security.SecuritySummary
import com.tagforge.domain.security.SessionCredentials
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
    private val credentials: SessionCredentials? = null,
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

    /** The password and PACK of the Set password under review, resolved when the tag (and so its UID) was read. */
    private var plannedCredential: SecurityPlans.NewCredential? = null

    init {
        viewModelScope.launch { availability.collect { value -> _state.update { it.copy(availability = value) } } }
        viewModelScope.launch { settings.collect { value -> _state.update { it.copy(advanced = value.advancedMode) } } }
        credentials?.let { store ->
            viewModelScope.launch { store.liveCount.collect { count -> _state.update { it.copy(rememberedCount = count) } } }
        }
    }

    // ---- the form ------------------------------------------------------------------------------------------------

    fun onChoose(action: SecurityAction) {
        wipe()
        val count = credentials?.rememberedUids()?.size ?: 0
        _state.update {
            SecurityUiState(availability = it.availability, advanced = it.advanced, rememberedCount = count, action = action, step = SecurityStep.FORM)
        }
        if (action == SecurityAction.UNLOCK || action == SecurityAction.REMOVE_PASSWORD) armLimitProbe()
    }

    fun onFormChange(transform: (SecurityForm) -> SecurityForm) {
        _state.update { it.copy(form = transform(it.form), problem = null, failure = null).withAuth0Clamped() }
    }

    /** The chip is known: AUTH0 may never leave its range (it must stay at or below CFG0, or the config stays writable). */
    private fun SecurityUiState.withModel(chip: NtagModel): SecurityUiState = copy(model = chip).withAuth0Clamped()

    private fun SecurityUiState.withAuth0Clamped(): SecurityUiState =
        if (form.auth0 in auth0Range) this else copy(form = form.copy(auth0 = form.auth0.coerceIn(auth0Range)))

    /** Flips between plain text and hexadecimal, keeping every typed value that has a form in the other mode. */
    fun onModeChange(mode: PasswordMode) {
        onFormChange { form ->
            if (form.mode == mode) return@onFormChange form
            fun convert(value: String) = PasswordInput.convert(form.mode, mode, value)
            val password = convert(form.password)
            form.copy(
                mode = mode,
                password = password,
                pack = convert(form.pack),
                currentPassword = convert(form.currentPassword),
                expectedPack = convert(form.expectedPack),
                storedAck = form.storedAck && password.isNotEmpty(),
            )
        }
    }

    /** A fresh password in the current mode. The PACK stays blank: a blank PACK means two random bytes per tag, shown after writing. */
    fun onGeneratePassword() {
        val mode = _state.value.form.mode
        val password = PasswordInput.generate(mode, random)
        onFormChange { it.copy(password = password, storedAck = false) }
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
                val count = credentials?.rememberedUids()?.size ?: 0
                _state.update { SecurityUiState(availability = it.availability, advanced = it.advanced, rememberedCount = count) }
                true
            }
            SecurityStep.CHOOSE -> false
        }
    }

    // ---- the sheet -------------------------------------------------------------------------------------------------

    /** Only a failure that offered a retry may be re-armed: a wrong or unknown password attempt is never sent again from here. */
    fun onRetry() {
        val sheet = _state.value.sheet as? SecuritySheet.Failure ?: return
        if (sheet.canRetry) rerun?.invoke()
    }

    fun onSheetDismissed() {
        val sheet = _state.value.sheet
        if (sheet is SecuritySheet.Working) return
        armJob?.cancel()
        disarm()
        _state.update { it.copy(sheet = null) }
    }

    /** Refreshes the remembered credential count (drops expired entries) when the screen returns to view. */
    fun onVisible() {
        val count = credentials?.rememberedUids()?.size ?: return
        _state.update { it.copy(rememberedCount = count) }
    }

    /** Clears every remembered tag password from memory immediately. */
    fun onForgetRemembered() {
        credentials?.forgetAll()
        _state.update { state ->
            val updatedSheet = (state.sheet as? SecuritySheet.Success)?.copy(remembered = false) ?: state.sheet
            state.copy(rememberedCount = 0, sheet = updatedSheet)
        }
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
        plannedCredential = null
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
        val credential = if (action == SecurityAction.SET_PASSWORD) SecurityPlans.newCredential(form, tagState.info.uid, random) else null
        if (action == SecurityAction.SET_PASSWORD && credential == null) {
            val problem = if (form.derive) FormProblem.PASSPHRASE_INVALID else FormProblem.PASSWORD_INVALID
            _state.update { it.copy(sheet = null, problem = problem, step = SecurityStep.FORM).withModel(tagState.model) }
            return
        }
        plannedCredential = credential
        when (val planned = SecurityPlans.plan(action, form, tagState, advanced, credential)) {
            is Outcome.Err -> _state.update { it.copy(sheet = null, failure = planned.error, step = SecurityStep.FORM).withModel(tagState.model) }
            is Outcome.Ok -> _state.update {
                it.copy(sheet = null, failure = null, plan = planned.value, typed = "", understood = false, step = SecurityStep.DRY_RUN)
                    .withModel(tagState.model)
            }
        }
    }

    private fun armExecute(action: SecurityAction) {
        val plan = confirmed ?: return
        val form = _state.value.form
        val newPassword = plannedCredential?.password.takeIf { action == SecurityAction.SET_PASSWORD }
        val newPack = plannedCredential?.pack.takeIf { action == SecurityAction.SET_PASSWORD }
        val operation = SecurityPlans.execution(action, plan)
        rerun = { armExecute(action) }
        runExecution(operation, action, plan, newPassword, newPack)
    }

    private fun armLimitProbe() {
        run(SecurityStage.READ, SecurityPlans.limitProbe()) { scan ->
            _state.update { state ->
                val probed = state.copy(sheet = null, authLimit = scan.value)
                scan.value.model?.let { chip -> probed.withModel(chip) } ?: probed
            }
        }
        rerun = { armLimitProbe() }
    }

    private fun armUnlock() {
        val form = _state.value.form
        val typedPassword = SecurityPlans.current(form)
        val expectedPack = SecurityPlans.expectedPack(form)
        val passphrase = form.passphrase.takeIf { form.derive }
        run(
            stage = SecurityStage.EXECUTE,
            operation = SecurityPlans.authenticate(form),
            onFailure = { scan ->
                val uid = scan.tag?.uid
                if (uid != null && isAuthRejection(scan.error)) credentials?.forget(uid)
            },
        ) { scan ->
            val report = scan.value
            val uid = scan.tag.uid
            val remembered = if (report.packMatches == false) {
                credentials?.forget(uid)
                false
            } else if (credentials != null && passphrase != null && form.rememberForSession) {
                // Only the DERIVED password and PACK for this UID are kept; the passphrase itself is never remembered.
                val (derived, derivedPack) = DiversifiedPassword.derive(passphrase, uid)
                credentials.remember(uid, derived, derivedPack)
                true
            } else if (credentials != null && typedPassword != null && form.rememberForSession) {
                credentials.remember(uid, typedPassword, expectedPack)
                true
            } else {
                false
            }
            _state.update {
                it.copy(
                    sheet = SecuritySheet.Success(SecurityAction.UNLOCK, report.pack, report.packMatches, remembered = remembered),
                    form = SecurityForm(),
                    rememberedCount = credentials?.rememberedUids()?.size ?: it.rememberedCount,
                )
            }
            eventChannel.trySend(SecurityEvent.Succeeded)
        }
        rerun = { onNext() }
    }

    private fun isAuthRejection(error: TagError): Boolean =
        error is TagError.AuthFailed ||
            error == TagError.Security(SecurityFailure.AuthExhausted) ||
            error == TagError.Security(SecurityFailure.AuthOutcomeUnknown)

    // ---- running ---------------------------------------------------------------------------------------------------

    private fun <T> runExecution(
        operation: TagOperation<T>,
        action: SecurityAction,
        plan: ConfirmedPlan,
        newPassword: Password?,
        newPack: Bytes?,
    ) {
        run(
            stage = SecurityStage.EXECUTE,
            operation = operation,
            onFailure = { scan ->
                recordFailure(plan, scan.tag, scan.error)
                if ((action == SecurityAction.SET_PASSWORD || action == SecurityAction.REMOVE_PASSWORD) &&
                    shouldClearCredential(scan.error, plan.plan.uid, scan.tag?.uid)
                ) {
                    credentials?.forget(plan.plan.uid)
                }
            },
        ) { scan ->
            recordHistory.recordSecurity(scan.tag, SecuritySummary.of(plan.plan.operation, null), null)
            val remembered = updateCredentialsAfterChange(action, scan.tag.uid, newPassword, newPack)
            wipe()
            _state.update {
                SecurityUiState(
                    availability = it.availability,
                    advanced = it.advanced,
                    rememberedCount = credentials?.rememberedUids()?.size ?: it.rememberedCount,
                    sheet = SecuritySheet.Success(action, remembered = remembered, newPack = newPack.takeIf { action == SecurityAction.SET_PASSWORD }),
                )
            }
            eventChannel.trySend(SecurityEvent.Succeeded)
        }
    }

    private fun updateCredentialsAfterChange(
        action: SecurityAction,
        uid: Bytes,
        newPassword: Password?,
        newPack: Bytes?,
    ): Boolean = when (action) {
        SecurityAction.SET_PASSWORD -> {
            credentials?.forget(uid)
            if (credentials != null && newPassword != null) {
                credentials.remember(uid, newPassword, newPack)
                true
            } else {
                false
            }
        }
        SecurityAction.REMOVE_PASSWORD -> {
            credentials?.forget(uid)
            false
        }
        else -> false
    }

    private fun shouldClearCredential(error: TagError, targetUid: Bytes, tappedUid: Bytes?): Boolean {
        if (tappedUid != targetUid) return false
        if (isAuthRejection(error)) return true
        val failure = (error as? TagError.Security)?.failure ?: return false
        return failure is SecurityFailure.Partial || failure is SecurityFailure.NotVerified
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
