package com.tagforge.app.feature.read

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.history.RecordHistoryUseCase
import com.tagforge.domain.read.TagScan
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.scan.ScanState
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State holder for the Read tab. It arms the read operation while the tab is visible (repeat mode: every tag that
 * touches the phone is read) and turns the coordinator's [ScanState] plus NFC availability into [ReadUiState].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadViewModel(
    private val coordinator: ScanCoordinator,
    private val operation: TagOperation<TagScan>,
    availability: Flow<NfcAvailability>,
    private val recordHistory: RecordHistoryUseCase,
) : ViewModel() {

    private val armed = MutableStateFlow<StateFlow<ScanState<TagScan>>?>(null)

    private val scanState: Flow<ScanState<TagScan>> = armed.flatMapLatest { it ?: flowOf(ScanState.Idle) }

    val uiState: StateFlow<ReadUiState> = combine(availability, scanState, ::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReadUiState.Waiting)

    private val eventChannel = Channel<ReadEvent>(Channel.BUFFERED)
    val events: Flow<ReadEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            scanState.distinctUntilChangedBy { it::class }.collect { state ->
                when {
                    state is ScanState.Working -> eventChannel.trySend(ReadEvent.TagDetected)
                    state is ScanState.Success -> {
                        eventChannel.trySend(ReadEvent.Succeeded)
                        recordHistory.recordRead(state.value)
                    }
                    // a cancelled arm and a protected tag are expected states, not failures to buzz about
                    state is ScanState.Failure && state.error != TagError.Cancelled -> eventChannel.trySend(ReadEvent.Failed)
                }
            }
        }
    }

    /** The tab became visible: start listening for tags. */
    fun onVisible() {
        if (armed.value == null) arm()
    }

    /** The tab is no longer visible: stop listening so a hidden screen never reads tags. */
    fun onHidden() {
        coordinator.disarmIfCurrent(operation)
        armed.value = null
    }

    /** Back to "ready to scan" after an error. */
    fun onRetry() = arm()

    fun onUnlockClicked() {
        eventChannel.trySend(ReadEvent.UnlockUnavailable)
    }

    override fun onCleared() {
        coordinator.disarmIfCurrent(operation)
    }

    private fun arm() {
        armed.value = coordinator.arm(operation, repeat = true)
    }

    private fun toUiState(availability: NfcAvailability, scan: ScanState<TagScan>): ReadUiState = when (availability) {
        NfcAvailability.UNSUPPORTED -> ReadUiState.Unsupported
        NfcAvailability.DISABLED -> ReadUiState.Disabled
        NfcAvailability.ENABLED -> when (scan) {
            ScanState.Idle, is ScanState.Waiting -> ReadUiState.Waiting
            is ScanState.Working -> ReadUiState.Reading(scan.phase)
            is ScanState.Success -> ReadUiState.Success(scan.value)
            is ScanState.Failure -> when (scan.error) {
                TagError.Cancelled -> ReadUiState.Waiting
                TagError.AuthRequired -> ReadUiState.Protected(scan.tag)
                else -> ReadUiState.Error(scan.error, scan.tag)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
