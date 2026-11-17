package com.tagforge.domain.security

/**
 * What an interrupted plan left on the tag, worked out from the plan's own write order and how far it got. Pure: no tag is read.
 * A write that was in flight when the link dropped may or may not have landed; the answer says UNKNOWN exactly when that
 * write is the one that decides it.
 */
internal data class PartialState(val protection: ProtectionState, val passwordPage: PasswordPageState)

internal object PartialProgress {

    private enum class Landed { YES, MAYBE, NO }

    /**
     * [completed] writes went through. [inFlightMayHaveLanded] is true when the write that failed (index [completed]) died with a
     * link error, so it may have been applied; false for a plain refusal and when no write failed (the read-back did).
     */
    fun of(plan: SecurityPlan, completed: Int, inFlightMayHaveLanded: Boolean): PartialState {
        val writes = plan.steps.filter { it !is PlanStep.Authenticate }
        fun landed(index: Int): Landed = when {
            index < 0 -> Landed.NO
            index < completed -> Landed.YES
            index == completed && inFlightMayHaveLanded -> Landed.MAYBE
            else -> Landed.NO
        }
        fun indexOfPage(page: Int) = writes.indexOfFirst { it is PlanStep.WritePage && it.page == page }

        val cfg0 = indexOfPage(plan.model.cfg0)
        val protection = when {
            cfg0 < 0 -> ProtectionState.UNKNOWN
            plan.operation == SecurityOperation.SET_PASSWORD -> when {
                plan.steps.firstOrNull() is PlanStep.Authenticate -> ProtectionState.PROTECTED   // it was protected and stays so
                else -> when (landed(cfg0)) {
                    Landed.YES -> ProtectionState.PROTECTED
                    Landed.MAYBE -> ProtectionState.UNKNOWN
                    Landed.NO -> ProtectionState.NOT_PROTECTED
                }
            }
            plan.operation == SecurityOperation.REMOVE_PASSWORD -> when (landed(cfg0)) {
                Landed.YES -> ProtectionState.NOT_PROTECTED
                Landed.MAYBE -> ProtectionState.UNKNOWN
                Landed.NO -> ProtectionState.PROTECTED
            }
            else -> ProtectionState.UNKNOWN
        }
        val passwordPage = when (landed(indexOfPage(plan.model.pwd))) {
            Landed.YES -> PasswordPageState.CHANGED
            Landed.MAYBE -> PasswordPageState.UNKNOWN
            Landed.NO -> PasswordPageState.UNCHANGED
        }
        return PartialState(protection, passwordPage)
    }
}
