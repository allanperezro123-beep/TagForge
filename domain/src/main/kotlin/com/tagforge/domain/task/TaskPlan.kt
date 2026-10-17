package com.tagforge.domain.task

/** One row of the confirmation sheet: the action, the verdict on it, and what it will really do on this device. */
data class PlanItem(
    val action: TaskAction,
    val check: ActionCheck,
    val capability: Capability,
) {
    val risk: TaskRisk get() = action.risk
    val isBlocked: Boolean get() = check is ActionCheck.Blocked
    val warning: ActionWarning? get() = (check as? ActionCheck.Warn)?.warning
}

/**
 * What the user is asked to confirm: every action of an untrusted tag payload, already validated. Building a plan has
 * no side effects. The only way to run it is [confirm], which yields the [ConfirmedTask] token [RunTaskUseCase] needs.
 */
class TaskPlan internal constructor(val name: String?, val items: List<PlanItem>) {

    /** True when an unencrypted HTTP request is part of the task: Run then needs a second, deliberate tap (TK-08). */
    val requiresSecondTap: Boolean get() = items.any { it.warning == ActionWarning.INSECURE_HTTP }

    /** Null while the second tap is still owed. */
    fun confirm(acknowledgedInsecure: Boolean): ConfirmedTask? =
        if (requiresSecondTap && !acknowledgedInsecure) null else ConfirmedTask(this)
}

/** Proof that the user pressed Run on [plan]. Only [TaskPlan.confirm] creates one. */
class ConfirmedTask internal constructor(val plan: TaskPlan)

object TaskPlanner {

    /** [capabilityOf] is a query only (see [com.tagforge.domain.ports.TaskActionExecutor.capability]). */
    fun plan(payload: TaskPayload, capabilityOf: (TaskAction) -> Capability): TaskPlan =
        TaskPlan(
            name = payload.name,
            items = payload.actions.map { PlanItem(it, ActionValidator.check(it), capabilityOf(it)) },
        )
}
