package com.tagforge.app.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.scan.ScanState
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a read-only tool is: waiting for a tag, reading it, or showing what it found. */
sealed interface ToolReading<out T> {
    data object Waiting : ToolReading<Nothing>
    data class Reading(val phase: OperationPhase) : ToolReading<Nothing>
    data class Result<out T>(val value: T, val tag: TagInfo) : ToolReading<T>

    /** [tag] is whatever was learned before the failure (null when no tag was reached). */
    data class Error(val error: TagError, val tag: TagInfo?) : ToolReading<Nothing>
}

sealed interface ToolReadEvent {
    data object TagDetected : ToolReadEvent
    data object Succeeded : ToolReadEvent
    data object Failed : ToolReadEvent
}

/**
 * Base for the tools that only READ a tag (memory dump, technology info): they arm while the screen is visible, show
 * the result of the first tag, and arm again on request. Nothing here can write to a tag.
 */
abstract class ScanToolViewModel<T>(
    private val coordinator: ScanCoordinator,
    private val operation: TagOperation<T>,
    availability: Flow<NfcAvailability>,
) : ViewModel() {

    protected val readingFlow = MutableStateFlow<ToolReading<T>>(ToolReading.Waiting)
    protected val availabilityFlow = MutableStateFlow(NfcAvailability.ENABLED)

    private val eventChannel = Channel<ToolReadEvent>(Channel.BUFFERED)
    val events: Flow<ToolReadEvent> = eventChannel.receiveAsFlow()

    private var armJob: Job? = null
    private var armed = false

    init {
        viewModelScope.launch { availability.collect { value -> availabilityFlow.value = value } }
    }

    /** The screen became visible: listen for a tag unless a result is already on screen. */
    fun onVisible() {
        if (availabilityFlow.value != NfcAvailability.ENABLED) return
        if (readingFlow.value == ToolReading.Waiting && !armed) arm()
    }

    /** "Scan another tag". */
    fun onRescan() {
        if (availabilityFlow.value != NfcAvailability.ENABLED) return
        arm()
    }

    /** The screen left the foreground: stop listening, but never abort a tag that is already being read. */
    fun onHidden() {
        if (readingFlow.value is ToolReading.Reading) return
        armJob?.cancel()
        coordinator.disarmIfCurrent(operation)
        armed = false
    }

    override fun onCleared() {
        coordinator.disarmIfCurrent(operation)
    }

    private fun arm() {
        armJob?.cancel()
        readingFlow.value = ToolReading.Waiting
        armed = true
        val scan = coordinator.arm(operation)

        var detected = false
        armJob = viewModelScope.launch {
            scan.collect { state ->
                if (state is ScanState.Working && !detected) {
                    detected = true
                    eventChannel.trySend(ToolReadEvent.TagDetected)
                }
                when (state) {
                    ScanState.Idle, is ScanState.Waiting -> Unit
                    is ScanState.Working -> readingFlow.update { ToolReading.Reading(state.phase) }
                    is ScanState.Success -> {
                        armed = false
                        readingFlow.value = ToolReading.Result(state.value, state.tag)
                        eventChannel.trySend(ToolReadEvent.Succeeded)
                    }
                    is ScanState.Failure -> {
                        armed = false
                        if (state.error == TagError.Cancelled) {
                            readingFlow.value = ToolReading.Waiting
                        } else {
                            readingFlow.value = ToolReading.Error(state.error, state.tag)
                            eventChannel.trySend(ToolReadEvent.Failed)
                        }
                    }
                }
            }
        }
    }
}
