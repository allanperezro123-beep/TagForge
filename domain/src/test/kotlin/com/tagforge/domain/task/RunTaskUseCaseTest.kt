package com.tagforge.domain.task

import com.tagforge.domain.fakes.FakeTaskActionExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TK-06 (nothing runs before an explicit confirmation) and TK-08 (HTTP consent), at the use case level. */
class RunTaskUseCaseTest {

    private val ringer = TaskAction.SetRinger(RingerMode.VIBRATE)
    private val url = TaskAction.OpenUrl("https://example.com")
    private val http = TaskAction.HttpRequest("https://example.com/hook")
    private val insecure = TaskAction.HttpRequest("http://192.168.1.5/on")

    private fun plan(executor: FakeTaskActionExecutor, vararg actions: TaskAction, name: String? = null) =
        TaskPlanner.plan(TaskPayload(name, actions.toList()), executor::capability)

    // ---- confirm / cancel -----------------------------------------------------------------------------------------

    @Test
    fun confirmRunsEveryActionInOrderAndReportsEachOutcome() = runTest {
        val executor = FakeTaskActionExecutor()
        val confirmed = assertNotNull(plan(executor, ringer, url).confirm(acknowledgedInsecure = false))

        val results = RunTaskUseCase(executor).run(confirmed)

        assertEquals(listOf(ringer, url), executor.executed)
        assertEquals(listOf(ringer, url), results.map { it.action })
        assertEquals(listOf(0, 1), results.map { it.index })
        assertEquals(listOf<ActionOutcome>(ActionOutcome.Done(), ActionOutcome.Done()), results.map { it.outcome })
    }

    @Test
    fun planningAndThenCancellingExecutesNothing() = runTest {
        val executor = FakeTaskActionExecutor()
        val plan = plan(executor, ringer, url)

        // The only way to execute is RunTaskUseCase.run(ConfirmedTask). Cancel simply never produces one.
        assertEquals(2, plan.items.size)
        assertTrue(executor.capabilityQueries >= 2, "the plan did ask about each action")
        assertEquals(emptyList(), executor.executed)
        assertEquals(0, executor.executeCalls)
    }

    @Test
    fun aFailureDoesNotStopTheRemainingActions() = runTest {
        val executor = FakeTaskActionExecutor(outcomes = { if (it == ringer) ActionOutcome.Failed(FailReason.UNEXPECTED) else ActionOutcome.Done() })
        val confirmed = assertNotNull(plan(executor, ringer, url).confirm(false))

        val results = RunTaskUseCase(executor).run(confirmed)

        assertEquals(ActionOutcome.Failed(FailReason.UNEXPECTED), results[0].outcome)
        assertEquals(ActionOutcome.Done(), results[1].outcome)
        assertEquals(listOf(ringer, url), executor.executed)
    }

    @Test
    fun anExceptionFromAnExecutorBecomesAFailedRowNeverACrash() = runTest {
        val executor = FakeTaskActionExecutor(throwOn = { it == ringer })
        val confirmed = assertNotNull(plan(executor, ringer, url).confirm(false))

        val results = RunTaskUseCase(executor).run(confirmed)

        assertEquals(ActionOutcome.Failed(FailReason.UNEXPECTED), results[0].outcome)
        assertEquals(ActionOutcome.Done(), results[1].outcome)
    }

    @Test
    fun cancellationOfTheRunningCoroutineIsNotSwallowed() = runTest {
        val executor = FakeTaskActionExecutor(throwOn = { true }, failure = CancellationException("stop"))
        val confirmed = assertNotNull(plan(executor, ringer).confirm(false))

        assertFailsWith<CancellationException> { RunTaskUseCase(executor).run(confirmed) }
    }

    @Test
    fun progressIsReportedAroundEveryAction() = runTest {
        val executor = FakeTaskActionExecutor()
        val confirmed = assertNotNull(plan(executor, ringer, url).confirm(false))
        val seen = mutableListOf<RunProgress>()

        RunTaskUseCase(executor).run(confirmed) { seen += it }

        assertEquals(
            listOf(
                RunProgress.Started(0), RunProgress.Finished(0, ActionOutcome.Done()),
                RunProgress.Started(1), RunProgress.Finished(1, ActionOutcome.Done()),
            ),
            seen,
        )
    }

    // ---- blocked actions ------------------------------------------------------------------------------------------

    @Test
    fun blockedActionsAreShownBlockedSkippedAndReportedWhileTheRestRuns() = runTest {
        val executor = FakeTaskActionExecutor()
        val evil = TaskAction.OpenUrl("intent://scan/#Intent;end")
        val plan = plan(executor, ringer, evil, url)

        assertEquals(ActionCheck.Blocked(BlockReason.DISALLOWED_SCHEME), plan.items[1].check)
        assertTrue(plan.items[1].isBlocked)
        assertFalse(plan.items[0].isBlocked)

        val results = RunTaskUseCase(executor).run(assertNotNull(plan.confirm(false)))

        assertEquals(listOf(ringer, url), executor.executed, "the blocked action never reaches an executor")
        assertEquals(ActionOutcome.Skipped(SkipReason.BLOCKED), results[1].outcome)
        assertEquals(3, results.size)
    }

    // ---- HTTP consent -----------------------------------------------------------------------------------------------

    @Test
    fun plainHttpNeedsASecondTapBeforeItCanBeConfirmed() = runTest {
        val executor = FakeTaskActionExecutor()
        val plan = plan(executor, ringer, insecure)

        assertTrue(plan.requiresSecondTap)
        assertNull(plan.confirm(acknowledgedInsecure = false), "first tap only shows the unencrypted warning")
        assertNotNull(plan.confirm(acknowledgedInsecure = true))
        assertEquals(0, executor.executeCalls)
    }

    @Test
    fun httpsAndOrdinaryTasksNeedNoSecondTap() {
        val executor = FakeTaskActionExecutor()

        assertFalse(plan(executor, ringer, http).requiresSecondTap)
        assertNotNull(plan(executor, ringer, http).confirm(false))
        assertFalse(plan(executor, ringer).requiresSecondTap)
    }

    @Test
    fun aBlockedHttpActionDoesNotDemandASecondTap() {
        val executor = FakeTaskActionExecutor()

        assertFalse(plan(executor, TaskAction.HttpRequest("ftp://host/x")).requiresSecondTap)
    }

    @Test
    fun httpRequestsRunOnlyWhileTheRunnerIsInTheForeground() = runTest {
        val executor = FakeTaskActionExecutor()
        val confirmed = assertNotNull(plan(executor, http, ringer).confirm(false))

        val results = RunTaskUseCase(executor, isForeground = { false }).run(confirmed)

        assertEquals(ActionOutcome.Skipped(SkipReason.NOT_FOREGROUND), results[0].outcome)
        assertEquals(listOf<TaskAction>(ringer), executor.executed, "the request itself was never sent")
        assertEquals(ActionOutcome.Done(), results[1].outcome)
    }

    @Test
    fun httpRequestsRunWhenTheRunnerIsInTheForeground() = runTest {
        val executor = FakeTaskActionExecutor(outcomes = { ActionOutcome.Done("200") })
        val confirmed = assertNotNull(plan(executor, http).confirm(false))

        val results = RunTaskUseCase(executor, isForeground = { true }).run(confirmed)

        assertEquals(listOf<TaskAction>(http), executor.executed)
        assertEquals(ActionOutcome.Done("200"), results.single().outcome)
    }

    // ---- what the sheet shows before running --------------------------------------------------------------------------

    @Test
    fun theSheetKnowsEachActionsRiskAndCapabilityBeforeAnythingRuns() {
        val executor = FakeTaskActionExecutor(capabilities = {
            if (it is TaskAction.SetBrightness) Capability.NeedsGrant(SpecialGrant.WRITE_SETTINGS) else Capability.Ready
        })
        val plan = plan(executor, TaskAction.SetBrightness(10), TaskAction.Dial("123"), name = "Night")

        assertEquals("Night", plan.name)
        assertEquals(Capability.NeedsGrant(SpecialGrant.WRITE_SETTINGS), plan.items[0].capability)
        assertEquals(TaskRisk.NONE, plan.items[0].risk)
        assertEquals(Capability.Ready, plan.items[1].capability)
        assertEquals(TaskRisk.CALL, plan.items[1].risk)
        assertIs<ActionCheck.Ok>(plan.items[1].check)
        assertEquals(0, executor.executeCalls, "asking about capability must have no side effects")
    }

    @Test
    fun anEmptyPlanRunsNothing() = runTest {
        val executor = FakeTaskActionExecutor()
        val plan = plan(executor)

        assertTrue(plan.items.isEmpty())
        val results = RunTaskUseCase(executor).run(assertNotNull(plan.confirm(false)))
        assertEquals(emptyList(), results)
    }
}
