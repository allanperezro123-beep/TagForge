package com.tagforge.domain.scan

import app.cash.turbine.test
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScanCoordinatorTest {

    /** Operation whose body is supplied by each test. */
    private class ProbeOp(
        override val kind: OperationKind = OperationKind.READ,
        override val timeoutMs: Long? = null,
        private val body: suspend (TagSession, (OperationPhase) -> Unit) -> TagResult<String>,
    ) : TagOperation<String> {
        var executions = 0
            private set

        override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<String> {
            executions++
            return body(session, phase)
        }
    }

    private fun session() = FakeTagSession.ntag(FakeNtag21x(NtagModel.NTAG213))

    // The TestScope itself is the coordinator scope: advanceUntilIdle() skips backgroundScope work, and every
    // job these tests start either completes or is cancelled by the test, so runTest can still finish.
    private fun TestScope.coordinator(timeoutMs: Long = 10_000) =
        ScanCoordinator(this, StandardTestDispatcher(testScheduler), timeoutMs)

    @Test
    fun walksIdleWaitingWorkingSuccess() = runTest {
        val coordinator = coordinator()
        val gate = CompletableDeferred<Unit>()
        val op = ProbeOp { _, phase ->
            phase(OperationPhase.READING)
            gate.await()
            Outcome.Ok("done")
        }
        val tag = session()

        assertEquals(ScanState.Idle, coordinator.state.value)
        coordinator.arm(op).test {
            assertEquals(ScanState.Waiting(OperationKind.READ), awaitItem())
            coordinator.onTagDiscovered(tag)
            assertEquals(ScanState.Working(OperationKind.READ, OperationPhase.CONNECTING), awaitItem())
            assertEquals(ScanState.Working(OperationKind.READ, OperationPhase.READING), awaitItem())
            gate.complete(Unit)
            assertEquals(ScanState.Success(OperationKind.READ, tag.info, "done"), awaitItem())
        }
        assertTrue(tag.closed, "session must be closed after the operation")
    }

    @Test
    fun coordinatorStateMirrorsTheCurrentArm() = runTest {
        val coordinator = coordinator()
        coordinator.arm(ProbeOp { _, _ -> Outcome.Ok("x") })
        assertEquals(ScanState.Waiting(OperationKind.READ), coordinator.state.value)

        coordinator.onTagDiscovered(session())
        advanceUntilIdle()

        assertIs<ScanState.Success<*>>(coordinator.state.value)
    }

    @Test
    fun tagsArrivingWhileWorkingAreIgnored() = runTest {
        val coordinator = coordinator()
        val gate = CompletableDeferred<Unit>()
        val op = ProbeOp { _, _ -> gate.await(); Outcome.Ok("done") }
        val first = session()
        val second = session()
        coordinator.arm(op)

        coordinator.onTagDiscovered(first)
        runCurrent()
        coordinator.onTagDiscovered(second)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, op.executions)
        assertFalse(second.closed, "the ignored tag is never touched")
    }

    @Test
    fun operationsTimeOutAfterTenSeconds() = runTest {
        val coordinator = coordinator()
        val op = ProbeOp { _, _ -> delay(20_000); Outcome.Ok("late") }
        val tag = session()
        coordinator.arm(op)
        coordinator.onTagDiscovered(tag)

        advanceTimeBy(9_999)
        assertIs<ScanState.Working>(coordinator.state.value)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(ScanState.Failure(OperationKind.READ, tag.info, TagError.Timeout), coordinator.state.value)
        assertTrue(tag.closed)
    }

    @Test
    fun anOperationCanAskForALongerTimeoutThanTheDefault() = runTest {
        val coordinator = coordinator()
        val op = ProbeOp(timeoutMs = 60_000) { _, _ -> delay(30_000); Outcome.Ok("slow but fine") }
        val tag = session()
        coordinator.arm(op)
        coordinator.onTagDiscovered(tag)

        advanceTimeBy(29_999)
        assertIs<ScanState.Working>(coordinator.state.value)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(ScanState.Success(OperationKind.READ, tag.info, "slow but fine"), coordinator.state.value)
    }

    @Test
    fun anOperationWithItsOwnTimeoutStillTimesOutAtThatLimit() = runTest {
        val coordinator = coordinator()
        val op = ProbeOp(timeoutMs = 60_000) { _, _ -> delay(90_000); Outcome.Ok("too late") }
        val tag = session()
        coordinator.arm(op)
        coordinator.onTagDiscovered(tag)

        advanceTimeBy(59_999)
        assertIs<ScanState.Working>(coordinator.state.value)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(ScanState.Failure(OperationKind.READ, tag.info, TagError.Timeout), coordinator.state.value)
    }

    @Test
    fun reArmingWhileWaitingCancelsThePreviousArm() = runTest {
        val coordinator = coordinator()
        val first = coordinator.arm(ProbeOp { _, _ -> Outcome.Ok("first") })

        val second = coordinator.arm(ProbeOp(OperationKind.MEMORY_DUMP) { _, _ -> Outcome.Ok("second") })

        assertEquals(ScanState.Failure(OperationKind.READ, null, TagError.Cancelled), first.value)
        assertEquals(ScanState.Waiting(OperationKind.MEMORY_DUMP), second.value)
        assertEquals(ScanState.Waiting(OperationKind.MEMORY_DUMP), coordinator.state.value)
    }

    @Test
    fun reArmingWhileWorkingCancelsTheRunningOperation() = runTest {
        val coordinator = coordinator()
        val never = CompletableDeferred<Unit>()
        val slow = ProbeOp { _, _ -> never.await(); Outcome.Ok("never") }
        val tag = session()
        val first = coordinator.arm(slow)
        coordinator.onTagDiscovered(tag)
        runCurrent()

        val second = coordinator.arm(ProbeOp { _, _ -> Outcome.Ok("second") })
        advanceUntilIdle()

        assertEquals(ScanState.Failure(OperationKind.READ, tag.info, TagError.Cancelled), first.value)
        assertTrue(tag.closed, "a cancelled operation still releases its tag")
        // the new arm works normally afterwards
        val next = session()
        coordinator.onTagDiscovered(next)
        advanceUntilIdle()
        assertEquals(ScanState.Success(OperationKind.READ, next.info, "second"), second.value)
    }

    @Test
    fun singleShotArmsDisarmAfterOneTag() = runTest {
        val coordinator = coordinator()
        val op = ProbeOp { _, _ -> Outcome.Ok("once") }
        coordinator.arm(op)

        coordinator.unarmedTags.test {
            coordinator.onTagDiscovered(session())
            advanceUntilIdle()
            coordinator.onTagDiscovered(session())   // nothing armed any more
            advanceUntilIdle()
            awaitItem()
        }
        assertEquals(1, op.executions)
    }

    @Test
    fun repeatArmsKeepRunningOnEveryTag() = runTest {
        val coordinator = coordinator()
        var counter = 0
        val op = ProbeOp { _, _ -> Outcome.Ok("run ${++counter}") }
        val flow = coordinator.arm(op, repeat = true)
        val firstTag = session()
        val secondTag = session()

        coordinator.onTagDiscovered(firstTag)
        advanceUntilIdle()
        assertEquals(ScanState.Success(OperationKind.READ, firstTag.info, "run 1"), flow.value)

        coordinator.onTagDiscovered(secondTag)
        advanceUntilIdle()
        assertEquals(ScanState.Success(OperationKind.READ, secondTag.info, "run 2"), flow.value)
        assertEquals(2, op.executions)
    }

    @Test
    fun tagLostIsAFailureNotACrash() = runTest {
        val coordinator = coordinator()
        val tag = session()
        val flow = coordinator.arm(ProbeOp { _, _ -> Outcome.Err(TagError.TagLost) })

        coordinator.onTagDiscovered(tag)
        advanceUntilIdle()

        assertEquals(ScanState.Failure(OperationKind.READ, tag.info, TagError.TagLost), flow.value)
        assertTrue(tag.closed)
    }

    @Test
    fun unexpectedExceptionsBecomeIoFailuresAndTheCoordinatorSurvives() = runTest {
        val coordinator = coordinator()
        val broken = coordinator.arm(ProbeOp { _, _ -> error("adapter bug") })
        coordinator.onTagDiscovered(session())
        advanceUntilIdle()
        val failure = broken.value
        assertIs<ScanState.Failure>(failure)
        assertEquals(TagError.Io("adapter bug"), failure.error)

        val healthy = coordinator.arm(ProbeOp { _, _ -> Outcome.Ok("fine") })
        coordinator.onTagDiscovered(session())
        advanceUntilIdle()
        assertIs<ScanState.Success<*>>(healthy.value)
    }

    @Test
    fun tagsWithNothingArmedOnlySignal() = runTest {
        val coordinator = coordinator()
        val tag = session()

        coordinator.unarmedTags.test {
            coordinator.onTagDiscovered(tag)
            awaitItem()
        }
        assertEquals(ScanState.Idle, coordinator.state.value)
        assertFalse(tag.closed)
    }

    @Test
    fun disarmReturnsToIdleAndIgnoresLaterTags() = runTest {
        val coordinator = coordinator()
        val op = ProbeOp { _, _ -> Outcome.Ok("x") }
        val flow = coordinator.arm(op)

        coordinator.disarm()
        coordinator.onTagDiscovered(session())
        advanceUntilIdle()

        assertEquals(ScanState.Idle, flow.value)
        assertEquals(ScanState.Idle, coordinator.state.value)
        assertEquals(0, op.executions)
    }
}
