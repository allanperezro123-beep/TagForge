package com.tagforge.domain.scan

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * The single state machine between "a tag appeared" and "an operation finished".
 *
 * One operation is armed at a time ([arm]). The first tag that arrives while it is armed runs it on [io] with a
 * timeout; tags that arrive while it is working are ignored. Failures (TagLost, timeout, adapter bugs) become
 * [ScanState.Failure] values: this class never throws to the NFC callback thread.
 */
class ScanCoordinator(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    private class Arm<T>(val op: TagOperation<T>, val repeat: Boolean) {
        val flow = MutableStateFlow<ScanState<T>>(ScanState.Waiting(op.kind))
        var job: Job? = null
        var session: TagSession? = null
        var working = false
    }

    private val lock = Any()
    private var current: Arm<*>? = null

    private val _state = MutableStateFlow<ScanState<Any?>>(ScanState.Idle)

    /** State of whichever arm is current (or the last finished one); Idle after [disarm]. */
    val state: StateFlow<ScanState<Any?>> = _state.asStateFlow()

    private val _unarmedTags = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits when a tag appears while nothing is armed, so the UI can hint "open Read to inspect tags". */
    val unarmedTags: SharedFlow<Unit> = _unarmedTags.asSharedFlow()

    /**
     * Arms [op]. Any previous arm is cancelled (its flow ends in Failure(Cancelled)). With [repeat] the arm survives
     * each tag (used by the Read tab); otherwise it disarms after the first tag it handles.
     */
    fun <T> arm(op: TagOperation<T>, repeat: Boolean = false): StateFlow<ScanState<T>> {
        val arm = Arm(op, repeat)
        val previous: Arm<*>?
        synchronized(lock) {
            previous = current
            current = arm
            _state.value = arm.flow.value
        }
        previous?.let(::cancel)
        return arm.flow.asStateFlow()
    }

    fun disarm() {
        val previous: Arm<*>?
        synchronized(lock) {
            previous = current
            current = null
            _state.value = ScanState.Idle
        }
        previous?.job?.cancel()
        previous?.setIdle()
    }

    /** Called from the NFC binder thread: non-blocking, never throws. */
    fun onTagDiscovered(session: TagSession) {
        val arm: Arm<*>
        synchronized(lock) {
            val armed = current
            if (armed == null) {
                _unarmedTags.tryEmit(Unit)
                return
            }
            if (armed.working) return
            armed.working = true
            arm = armed
        }
        arm.job = scope.launch(io) { run(arm, session) }
    }

    private suspend fun <T> run(arm: Arm<T>, session: TagSession) {
        val kind = arm.op.kind
        arm.session = session
        publish(arm, ScanState.Working(kind, OperationPhase.CONNECTING))

        try {
            val result: TagResult<T> = try {
                withTimeout(timeoutMs) { arm.op.execute(session) { phase -> publish(arm, ScanState.Working(kind, phase)) } }
            } catch (_: TimeoutCancellationException) {
                Outcome.Err(TagError.Timeout)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.Err(TagError.Io(e.message))
            }
            finish(arm, result, session)
        } finally {
            withContext(NonCancellable) { runCatching { session.close() } }
            synchronized(lock) { arm.working = false }
        }
    }

    private fun <T> finish(arm: Arm<T>, result: TagResult<T>, session: TagSession) {
        val kind = arm.op.kind
        val final: ScanState<T> = when (result) {
            is Outcome.Ok -> ScanState.Success(kind, session.info, result.value)
            is Outcome.Err -> ScanState.Failure(kind, session.info, result.error)
        }
        synchronized(lock) {
            if (current !== arm) return
            arm.flow.value = final
            _state.value = final
            if (!arm.repeat) current = null
        }
    }

    private fun <T> publish(arm: Arm<T>, state: ScanState<T>) {
        synchronized(lock) {
            if (current !== arm) return
            arm.flow.value = state
            _state.value = state
        }
    }

    /** Ends a superseded arm: a running operation is cancelled and the arm's flow reports Failure(Cancelled). */
    private fun <T> cancel(arm: Arm<T>) {
        arm.job?.cancel()
        val kind = arm.op.kind
        when (arm.flow.value) {
            is ScanState.Waiting, is ScanState.Working ->
                arm.flow.value = ScanState.Failure(kind, arm.session?.info, TagError.Cancelled)
            else -> Unit
        }
    }

    private fun <T> Arm<T>.setIdle() {
        flow.value = ScanState.Idle
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
