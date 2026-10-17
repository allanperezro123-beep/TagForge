package com.tagforge.app.feature.taskrunner

import app.cash.turbine.test
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeTaskActionExecutor
import com.tagforge.domain.fakes.FixedClock
import com.tagforge.domain.fakes.InMemoryTaskRepository
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ports.TaskActionExecutor
import com.tagforge.domain.task.ActionCheck
import com.tagforge.domain.task.ActionOutcome
import com.tagforge.domain.task.BlockReason
import com.tagforge.domain.task.Capability
import com.tagforge.domain.task.FailReason
import com.tagforge.domain.task.HttpMethod
import com.tagforge.domain.task.RingerMode
import com.tagforge.domain.task.SkipReason
import com.tagforge.domain.task.SpecialGrant
import com.tagforge.domain.task.Task
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.task.TaskError
import com.tagforge.domain.task.TaskMessageBuilder
import com.tagforge.domain.task.TaskPayload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/** TK-05, TK-06, TK-08 at the state-holder level: what the runner shows, and that nothing runs before Run. */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskRunnerViewModelTest {

    private val executor = FakeTaskActionExecutor()
    private val repository = InMemoryTaskRepository()
    private val clock = FixedClock(Instant.fromEpochMilliseconds(7_000))
    private var foreground = true

    private val ringer = TaskAction.SetRinger(RingerMode.VIBRATE)
    private val url = TaskAction.OpenUrl("https://example.com")

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(taskExecutor: TaskActionExecutor = executor) =
        TaskRunnerViewModel(taskExecutor, repository, clock, isForeground = { foreground })

    private fun messageBytes(vararg actions: TaskAction, name: String? = null): Bytes {
        val message = (TaskMessageBuilder.build(TaskPayload(name, actions.toList()), "com.tagforge.app") as Outcome.Ok).value
        return DefaultNdefCodec.encode(message)
    }

    private fun TaskRunnerViewModel.confirming() = assertIs<TaskRunnerUiState.Confirming>(uiState.value)

    // ---- cold start: the sheet, nothing else ---------------------------------------------------------------------

    @Test
    fun aScannedTaskOpensTheConfirmationSheetAndRunsNothing() = runTest {
        val vm = viewModel()

        vm.onNdefBytes(messageBytes(ringer, url, name = "Night"))

        val state = vm.confirming()
        assertEquals("Night", state.plan.name)
        assertEquals(listOf(ringer, url), state.plan.items.map { it.action })
        assertEquals(0, executor.executeCalls)
    }

    @Test
    fun noTimeoutEverConfirmsATask() = runTest {
        val vm = viewModel()
        vm.onNdefBytes(messageBytes(ringer))

        advanceTimeBy(10 * 60 * 1000L)
        advanceUntilIdle()

        assertIs<TaskRunnerUiState.Confirming>(vm.uiState.value)
        assertEquals(0, executor.executeCalls)
    }

    @Test
    fun beforeAnyIntentThereIsNothingToShow() {
        assertEquals(TaskRunnerUiState.Idle, viewModel().uiState.value)
    }

    // ---- confirm / cancel -------------------------------------------------------------------------------------------

    @Test
    fun runExecutesTheActionsInOrderAndShowsEachResult() = runTest {
        val vm = viewModel()
        vm.onNdefBytes(messageBytes(ringer, url))

        vm.onRun()

        assertEquals(listOf(ringer, url), executor.executed)
        val done = assertIs<TaskRunnerUiState.Finished>(vm.uiState.value)
        assertEquals(listOf<ActionOutcome>(ActionOutcome.Done(), ActionOutcome.Done()), done.results.map { it.outcome })
    }

    @Test
    fun cancelClosesWithZeroActionsExecuted() = runTest {
        val vm = viewModel()
        vm.onNdefBytes(messageBytes(ringer, url))

        vm.events.test {
            vm.onCancel()

            assertEquals(RunnerEvent.Close, awaitItem())
        }
        assertEquals(0, executor.executeCalls)
        assertEquals(TaskRunnerUiState.Idle, vm.uiState.value)
    }

    @Test
    fun backAndOutsideTapAreCancel() = runTest {
        val vm = viewModel()
        vm.onNdefBytes(messageBytes(ringer))

        vm.events.test {
            vm.onDismiss()

            assertEquals(RunnerEvent.Close, awaitItem())
        }
        assertEquals(0, executor.executeCalls)
    }

    @Test
    fun runIsIgnoredWhenThereIsNothingToConfirm() = runTest {
        val vm = viewModel()

        vm.onRun()

        assertEquals(TaskRunnerUiState.Idle, vm.uiState.value)
        assertEquals(0, executor.executeCalls)
    }

    @Test
    fun anEmptyTaskCannotBeRun() = runTest {
        val vm = viewModel()
        vm.onNdefBytes(messageBytes())

        assertFalse(vm.confirming().canRun)
        vm.onRun()

        assertIs<TaskRunnerUiState.Confirming>(vm.uiState.value)
        assertEquals(0, executor.executeCalls)
    }

    // ---- hostile or broken tags ---------------------------------------------------------------------------------------

    @Test
    fun anUnknownVersionIsAnErrorScreenWithNothingExecuted() = runTest {
        val vm = viewModel()
        val record = NdefRecord.mime("application/x-tf", Bytes.of(0x20, 0x00))

        vm.onNdefBytes(DefaultNdefCodec.encode(NdefMessage(listOf(record))))

        assertEquals(TaskRunnerUiState.Error(TaskError.UnsupportedVersion(2)), vm.uiState.value)
        assertEquals(0, executor.executeCalls)
    }

    @Test
    fun twoHundredActionsIsTooLargeAndNeverPartiallyExecuted() = runTest {
        val vm = viewModel()
        val payload = Bytes.of(0x10, 200) + Bytes.concat(List(200) { Bytes.of(0x03, 0x01) })

        vm.onNdefBytes(DefaultNdefCodec.encode(NdefMessage(listOf(NdefRecord.mime("application/x-tf", payload)))))

        assertEquals(TaskRunnerUiState.Error(TaskError.TooLarge), vm.uiState.value)
        assertEquals(0, executor.executeCalls)
    }

    @Test
    fun anUnknownOpcodeIsRejectedWholesale() = runTest {
        val vm = viewModel()

        vm.onNdefBytes(DefaultNdefCodec.encode(NdefMessage(listOf(NdefRecord.mime("application/x-tf", Bytes.of(0x10, 0x02, 0x03, 0x01, 0x7F))))))

        assertEquals(TaskRunnerUiState.Error(TaskError.UnknownOpcode(0x7F)), vm.uiState.value)
        assertEquals(0, executor.executeCalls, "the ringer action before the bad opcode must not run either")
    }

    @Test
    fun bytesThatAreNotNdefAreAnErrorNotACrash() = runTest {
        val vm = viewModel()

        vm.onNdefBytes(Bytes.of(0xFF, 0x00, 0x13))

        assertIs<TaskRunnerUiState.Error>(vm.uiState.value)
        assertEquals(0, executor.executeCalls)
    }

    @Test
    fun aTagWithoutATaskRecordIsNotATask() = runTest {
        val vm = viewModel()

        vm.onNdefBytes(DefaultNdefCodec.encode(NdefMessage(listOf(NdefRecord.text("hello")))))

        assertEquals(TaskRunnerUiState.Error(TaskError.NotATask), vm.uiState.value)
    }

    @Test
    fun blockedActionsAreShownBlockedAndSkippedWhileTheRestRuns() = runTest {
        val vm = viewModel()
        val evil = TaskAction.OpenUrl("intent://scan/#Intent;end")
        vm.onNdefBytes(messageBytes(ringer, evil))

        val state = vm.confirming()
        assertEquals(ActionCheck.Blocked(BlockReason.DISALLOWED_SCHEME), state.plan.items[1].check)

        vm.onRun()

        assertEquals(listOf<TaskAction>(ringer), executor.executed)
        val done = assertIs<TaskRunnerUiState.Finished>(vm.uiState.value)
        assertEquals(ActionOutcome.Skipped(SkipReason.BLOCKED), done.results[1].outcome)
    }

    // ---- HTTP consent ---------------------------------------------------------------------------------------------------

    @Test
    fun plainHttpNeedsASecondTapAndTheFirstOneRunsNothing() = runTest {
        val vm = viewModel()
        vm.onNdefBytes(messageBytes(TaskAction.HttpRequest("http://192.168.1.5/on")))
        assertTrue(vm.confirming().plan.requiresSecondTap)
        assertFalse(vm.confirming().armed)

        vm.onRun()

        assertTrue(vm.confirming().armed, "first tap only arms the unencrypted warning")
        assertEquals(0, executor.executeCalls)

        vm.onRun()

        assertEquals(1, executor.executeCalls)
        assertIs<TaskRunnerUiState.Finished>(vm.uiState.value)
    }

    @Test
    fun httpsRunsOnTheFirstTap() = runTest {
        val vm = viewModel()
        vm.onNdefBytes(messageBytes(TaskAction.HttpRequest("https://example.com/hook", HttpMethod.POST)))

        vm.onRun()

        assertEquals(1, executor.executeCalls)
    }

    @Test
    fun httpIsSkippedWhenTheRunnerIsNotOnScreen() = runTest {
        val vm = viewModel()
        vm.onNdefBytes(messageBytes(TaskAction.HttpRequest("https://example.com/hook")))
        foreground = false

        vm.onRun()

        assertEquals(0, executor.executeCalls)
        val done = assertIs<TaskRunnerUiState.Finished>(vm.uiState.value)
        assertEquals(ActionOutcome.Skipped(SkipReason.NOT_FOREGROUND), done.results.single().outcome)
    }

    // ---- second scan while one is pending ---------------------------------------------------------------------------------

    @Test
    fun aNewIntentReplacesThePendingTaskAndResetsTheSecondTap() = runTest {
        val vm = viewModel()
        vm.onNdefBytes(messageBytes(TaskAction.HttpRequest("http://a.b/x"), name = "First"))
        vm.onRun()
        assertTrue(vm.confirming().armed)

        vm.onNdefBytes(messageBytes(ringer, name = "Second"))

        val state = vm.confirming()
        assertEquals("Second", state.plan.name)
        assertFalse(state.armed)
        assertEquals(0, executor.executeCalls)
    }

    @Test
    fun aNewIntentWhileRunningIsIgnored() = runTest {
        val gate = GatedExecutor()
        val vm = viewModel(gate)
        vm.onNdefBytes(messageBytes(ringer, name = "Running"))
        vm.onRun()
        runCurrent()
        assertIs<TaskRunnerUiState.Running>(vm.uiState.value)

        vm.onNdefBytes(messageBytes(url, name = "Late"))

        assertIs<TaskRunnerUiState.Running>(vm.uiState.value)
        gate.release()
        advanceUntilIdle()
        assertEquals("Running", assertIs<TaskRunnerUiState.Finished>(vm.uiState.value).plan.name)
    }

    @Test
    fun cancellingWhileRunningStopsTheRemainingActions() = runTest {
        val gate = GatedExecutor()
        val vm = viewModel(gate)
        vm.onNdefBytes(messageBytes(ringer, url))
        vm.onRun()
        runCurrent()
        assertEquals(listOf<TaskAction>(ringer), gate.started, "the first action is in flight")

        vm.events.test {
            vm.onCancel()
            assertEquals(RunnerEvent.Close, awaitItem())
        }
        gate.release()
        advanceUntilIdle()

        assertEquals(listOf<TaskAction>(ringer), gate.started, "the second action never started")
    }

    @Test
    fun runningShowsPerRowProgress() = runTest {
        val gate = GatedExecutor()
        val vm = viewModel(gate)
        vm.onNdefBytes(messageBytes(ringer, url))

        vm.onRun()
        runCurrent()

        val running = assertIs<TaskRunnerUiState.Running>(vm.uiState.value)
        assertEquals(listOf(RowStatus.Running, RowStatus.Pending), running.rows.map { it.status })
        gate.release()
        advanceUntilIdle()
        assertIs<TaskRunnerUiState.Finished>(vm.uiState.value)
    }

    // ---- summary and auto-close -------------------------------------------------------------------------------------------

    @Test
    fun aCleanRunClosesItselfAfterOneAndAHalfSeconds() = runTest {
        val vm = viewModel()
        vm.onNdefBytes(messageBytes(ringer))

        vm.events.test {
            vm.onRun()
            expectNoEvents()
            advanceTimeBy(1_499)
            expectNoEvents()
            advanceTimeBy(2)
            assertEquals(RunnerEvent.Close, awaitItem())
        }
    }

    @Test
    fun aRunThatNeedsTheUserStaysOpenSoTheyCanReadAndFix() = runTest {
        val needs = FakeTaskActionExecutor(
            capabilities = { Capability.NeedsGrant(SpecialGrant.WRITE_SETTINGS) },
            outcomes = { ActionOutcome.PermissionNeeded(SpecialGrant.WRITE_SETTINGS) },
        )
        val vm = viewModel(needs)
        vm.onNdefBytes(messageBytes(TaskAction.SetBrightness(100)))

        vm.events.test {
            vm.onRun()
            advanceTimeBy(60_000)
            expectNoEvents()
        }
        val done = assertIs<TaskRunnerUiState.Finished>(vm.uiState.value)
        assertFalse(done.autoClose)
        assertTrue(done.needsAttention)
    }

    @Test
    fun aFailedActionAlsoKeepsTheSummaryOpen() = runTest {
        val failing = FakeTaskActionExecutor(outcomes = { ActionOutcome.Failed(FailReason.NOT_INSTALLED) })
        val vm = viewModel(failing)
        vm.onNdefBytes(messageBytes(TaskAction.LaunchApp("com.missing.app")))

        vm.onRun()

        assertFalse(assertIs<TaskRunnerUiState.Finished>(vm.uiState.value).autoClose)
    }

    @Test
    fun theSheetKnowsWhichActionsNeedAGrantBeforeRunning() = runTest {
        val needs = FakeTaskActionExecutor(capabilities = { if (it is TaskAction.SetBrightness) Capability.NeedsGrant(SpecialGrant.WRITE_SETTINGS) else Capability.Ready })
        val vm = viewModel(needs)

        vm.onNdefBytes(messageBytes(ringer, TaskAction.SetBrightness(100)))

        val items = vm.confirming().plan.items
        assertEquals(Capability.Ready, items[0].capability)
        assertEquals(Capability.NeedsGrant(SpecialGrant.WRITE_SETTINGS), items[1].capability)
        assertTrue(vm.confirming().anyNeedsGrant)
    }

    // ---- saved tasks ("run now") --------------------------------------------------------------------------------------------

    @Test
    fun runningASavedTaskGoesThroughTheSameConfirmationAndRecordsTheRun() = runTest {
        val id = repository.upsert(Task(name = "Saved", actions = listOf(ringer), createdAt = Instant.fromEpochMilliseconds(1), updatedAt = Instant.fromEpochMilliseconds(1)))
        val vm = viewModel()

        vm.onTaskId(id)
        runCurrent()

        assertEquals("Saved", vm.confirming().plan.name)
        assertEquals(0, executor.executeCalls)
        assertEquals(null, repository.get(id)!!.lastRunAt, "not run yet")

        vm.onRun()
        advanceUntilIdle()

        assertEquals(listOf<TaskAction>(ringer), executor.executed)
        assertEquals(clock.now(), repository.get(id)!!.lastRunAt)
    }

    @Test
    fun aMissingSavedTaskIsNotATask() = runTest {
        val vm = viewModel()

        vm.onTaskId(404)
        runCurrent()

        assertEquals(TaskRunnerUiState.Error(TaskError.NotATask), vm.uiState.value)
    }

    @Test
    fun aTagTaskIsNeverWrittenToTheRepository() = runTest {
        val vm = viewModel()
        vm.onNdefBytes(messageBytes(ringer))

        vm.onRun()
        advanceUntilIdle()

        assertIs<TaskRunnerUiState.Finished>(vm.uiState.value)
        assertEquals(emptyList(), repository.observeAll().first(), "a task read from a tag is not saved unless the user saves it")
    }

    /** Holds the first action until [release], so tests can look at the Running state. */
    private class GatedExecutor : TaskActionExecutor {
        private val gate = CompletableDeferred<Unit>()
        val started = mutableListOf<TaskAction>()

        fun release() = gate.complete(Unit)

        override fun capability(action: TaskAction): Capability = Capability.Ready

        override suspend fun execute(action: TaskAction): ActionOutcome {
            started += action
            gate.await()
            return ActionOutcome.Done()
        }
    }
}
