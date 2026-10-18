package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation

/** Switches password protection off again (NS-04): the tag returns to its factory state for AUTH0, PROT, PWD and PACK. */
class RemovePasswordUseCase(private val confirmed: ConfirmedPlan) : TagOperation<SecurityReport> {
    override val kind: OperationKind = OperationKind.SECURITY

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<SecurityReport> =
        SecurityExecutor.run(session, confirmed, SecurityOperation.REMOVE_PASSWORD, phase)

    companion object {
        private val FACTORY_PWD = Bytes.of(0xFF, 0xFF, 0xFF, 0xFF)
        private val FACTORY_PACK = Bytes.of(0x00, 0x00, 0x00, 0x00)
        private const val AUTH0_DISABLED = 0xFF

        /**
         * [state] must have been read WITH [password] (the configuration is behind it). Order: authenticate, AUTH0 = FFh
         * FIRST (so an interruption leaves the tag open, never half protected), then PROT, then PWD, then PACK.
         */
        fun plan(state: SecurityState, password: Password): Outcome<SecurityPlan, SecurityFailure> {
            val model = state.model
            val config = state.config
            if (!state.isPasswordProtected) return Outcome.Err(SecurityFailure.NotProtected)
            if (config.cfgLock) return Outcome.Err(SecurityFailure.Unremovable)

            val open = config.withAuth0(AUTH0_DISABLED)
            val writable = open.withProt(false)
            return Outcome.Ok(
                SecurityPlan(
                    operation = SecurityOperation.REMOVE_PASSWORD,
                    uid = state.info.uid,
                    model = model,
                    steps = listOf(
                        PlanStep.Authenticate(password.bytes),
                        PlanStep.WritePage(model.cfg0, old = config.cfg0, new = open.cfg0),
                        PlanStep.WritePage(model.cfg1, old = config.cfg1, new = writable.cfg1),
                        PlanStep.WritePage(model.pwd, old = null, new = FACTORY_PWD),
                        PlanStep.WritePage(model.pack, old = null, new = FACTORY_PACK),
                    ),
                    effects = emptySet(),
                    checks = listOf(
                        Check.AuthWorks(FACTORY_PWD, Bytes.of(0x00, 0x00)),
                        Check.PageEquals(model.cfg0, open.cfg0),
                        Check.PageEquals(model.cfg1, writable.cfg1),
                    ),
                ),
            )
        }
    }
}
