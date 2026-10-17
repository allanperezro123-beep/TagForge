package com.tagforge.domain.fakes

import com.tagforge.domain.ports.TaskActionExecutor
import com.tagforge.domain.task.ActionOutcome
import com.tagforge.domain.task.Capability
import com.tagforge.domain.task.TaskAction

/** Records what a run would do. [capabilities] and [outcomes] script the answers; [throwOn] makes an action blow up. */
class FakeTaskActionExecutor(
    private val capabilities: (TaskAction) -> Capability = { Capability.Ready },
    private val outcomes: (TaskAction) -> ActionOutcome = { ActionOutcome.Done() },
    private val throwOn: (TaskAction) -> Boolean = { false },
    private val failure: Throwable = IllegalStateException("boom"),
) : TaskActionExecutor {

    val executed = mutableListOf<TaskAction>()

    val executeCalls: Int get() = executed.size

    var capabilityQueries: Int = 0
        private set

    override fun capability(action: TaskAction): Capability {
        capabilityQueries++
        return capabilities(action)
    }

    override suspend fun execute(action: TaskAction): ActionOutcome {
        if (throwOn(action)) throw failure
        executed += action
        return outcomes(action)
    }
}
