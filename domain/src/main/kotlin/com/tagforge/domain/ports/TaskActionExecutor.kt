package com.tagforge.domain.ports

import com.tagforge.domain.task.ActionOutcome
import com.tagforge.domain.task.Capability
import com.tagforge.domain.task.TaskAction

/** Android side of running a task. [capability] is a pure query (no side effects, no prompts); [execute] does the work. */
interface TaskActionExecutor {
    fun capability(action: TaskAction): Capability

    /** Must not throw for expected failures: report them as [ActionOutcome.Failed]. Called only for confirmed, valid actions. */
    suspend fun execute(action: TaskAction): ActionOutcome
}
