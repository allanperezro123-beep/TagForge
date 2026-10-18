package com.tagforge.domain.security

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.NtagMemoryMap
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech

/** What a plan did. Only ever returned when every step ran and every check passed. */
data class SecurityReport(val operation: SecurityOperation, val tag: TagInfo, val writes: Int)

/**
 * Runs a [ConfirmedPlan] (NS-01): the tag must be the confirmed one (checked before a single frame is sent), the pages
 * must still hold what the plan was built from (read just before the first write), the steps run in order, and success
 * is reported only after the read-back checks pass. A failure after a write may have landed is a [SecurityFailure.Partial],
 * never a success and never silent.
 */
internal object SecurityExecutor {

    suspend fun run(
        session: TagSession,
        confirmed: ConfirmedPlan,
        expected: SecurityOperation,
        phase: (OperationPhase) -> Unit,
    ): TagResult<SecurityReport> {
        val plan = confirmed.plan
        if (plan.operation != expected) return fail(SecurityFailure.WrongPlan)
        if (!plan.isForTag(session.info.uid)) return fail(SecurityFailure.UidMismatch)

        phase(OperationPhase.CONNECTING)
        val info = when (val identified = session.identify()) {
            is Outcome.Err -> return identified
            is Outcome.Ok -> identified.value
        }
        val t = session.transceiver(TagTech.NFC_A)
        if (t == null || info.ntag != plan.model) return fail(SecurityFailure.UnsupportedChip)

        val leading = plan.steps.takeWhile { it is PlanStep.Authenticate }
        for (step in leading) authenticate(t, step as PlanStep.Authenticate).let { if (it is Outcome.Err) return it }
        val rest = plan.steps.drop(leading.size)

        phase(OperationPhase.READING)
        preflight(t, plan, rest)?.let { return it }

        phase(OperationPhase.WRITING)
        val total = rest.count { it !is PlanStep.Authenticate }
        var completed = 0
        for (step in rest) {
            val result = when (step) {
                is PlanStep.WritePage -> SecurityIo.writePage(t, step.page, step.new)
                is PlanStep.Authenticate -> authenticate(t, step)
                is PlanStep.Raw -> SecurityIo.sendRaw(t, step.frame)
                PlanStep.MakeReadOnly -> session.makeReadOnly()
            }
            if (result is Outcome.Err) return partial(plan, result.error, completed, total, step)
            if (step !is PlanStep.Authenticate) completed++
        }

        phase(OperationPhase.VERIFYING)
        when (val checked = CheckRunner.run(session, t, plan.checks)) {
            is Outcome.Err -> return if (checked.error is TagError.Security) checked else partial(plan, checked.error, total, total, null)
            is Outcome.Ok -> Unit
        }
        return Outcome.Ok(SecurityReport(plan.operation, session.info, total))
    }

    private suspend fun authenticate(t: TagTransceiver, step: PlanStep.Authenticate): TagResult<Unit> =
        when (val r = SecurityIo.authenticate(t, step.password)) {
            is Outcome.Ok -> Outcome.Ok(Unit)
            is Outcome.Err -> r
        }

    /** Every page the plan will overwrite and that the chip lets us read must still hold the planned old value. */
    private suspend fun preflight(t: TagTransceiver, plan: SecurityPlan, steps: List<PlanStep>): TagResult<Nothing>? {
        for (step in steps.filterIsInstance<PlanStep.WritePage>()) {
            val old = step.old ?: continue
            if (NtagMemoryMap.regionsOf(plan.model, step.page).firstOrNull()?.hiddenByChip == true) continue
            when (val read = SecurityIo.readPages(t, step.page, step.page)) {
                is Outcome.Err -> return read
                is Outcome.Ok -> if (read.value != old) return fail(SecurityFailure.StateChanged)
            }
        }
        return null
    }

    /** A tag that vanished (or erred) around a write may have applied it; a plain refusal on the first write changed nothing. */
    private fun partial(plan: SecurityPlan, error: TagError, completed: Int, total: Int, step: PlanStep?): TagResult<Nothing> {
        if (error is TagError.Security) return Outcome.Err(error)
        val maybeApplied = error is TagError.TagLost || error is TagError.Io || error is TagError.Timeout
        val authStep = step is PlanStep.Authenticate
        if (!maybeApplied && completed == 0 || authStep && completed == 0) return Outcome.Err(error)
        return fail(SecurityFailure.Partial(plan.operation, error, completed, total))
    }

    private fun fail(failure: SecurityFailure): TagResult<Nothing> = Outcome.Err(TagError.Security(failure))
}
