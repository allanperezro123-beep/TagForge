package com.tagforge.domain.task

import com.tagforge.domain.ports.TaskActionExecutor
import kotlinx.coroutines.CancellationException

/** Outcome of one action together with where it sat in the task. */
data class ActionResult(val index: Int, val action: TaskAction, val outcome: ActionOutcome)

/** Lets the sheet show per-row progress while the task runs. */
sealed interface RunProgress {
    data class Started(val index: Int) : RunProgress
    data class Finished(val index: Int, val outcome: ActionOutcome) : RunProgress
}

/**
 * Runs a confirmed task: sequentially, in order, one outcome per action (TK-06). Blocked actions are skipped and
 * reported, an unencrypted-or-not HTTP request only runs while the runner is on screen (TK-08), and a failing action
 * never stops the ones after it. There is no timeout path to confirmation: the only input is a [ConfirmedTask].
 *
 * @param isForeground evaluated right before each HTTP request
 */
class RunTaskUseCase(
    private val executor: TaskActionExecutor,
    private val isForeground: () -> Boolean = { true },
) {

    suspend fun run(confirmed: ConfirmedTask, onProgress: (RunProgress) -> Unit = {}): List<ActionResult> {
        val results = ArrayList<ActionResult>(confirmed.plan.items.size)
        confirmed.plan.items.forEachIndexed { index, item ->
            onProgress(RunProgress.Started(index))
            val outcome = outcomeOf(item)
            results += ActionResult(index, item.action, outcome)
            onProgress(RunProgress.Finished(index, outcome))
        }
        return results
    }

    private suspend fun outcomeOf(item: PlanItem): ActionOutcome = when {
        item.isBlocked -> ActionOutcome.Skipped(SkipReason.BLOCKED)
        item.action is TaskAction.HttpRequest && !isForeground() -> ActionOutcome.Skipped(SkipReason.NOT_FOREGROUND)
        else -> try {
            executor.execute(item.action)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ActionOutcome.Failed(FailReason.UNEXPECTED)
        }
    }
}
