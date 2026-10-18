package com.tagforge.domain.security

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation

/**
 * What to freeze in the configuration pages (NS-05). Always its own request, plan and typed confirmation: it can never
 * ride along with [PasswordRequest]. [authLimit] 0 means "do not change the limit".
 */
data class ConfigLockRequest(
    val lockConfig: Boolean = false,
    val authLimit: Int = 0,
    val currentPassword: Password? = null,
) {
    init {
        require(authLimit in 0..MAX_AUTH_LIMIT) { "AUTHLIM is 3 bits (0..$MAX_AUTH_LIMIT) but got $authLimit" }
    }

    private companion object {
        const val MAX_AUTH_LIMIT = 7
    }
}

/**
 * Sets CFGLCK (CFG0 and CFG1 can never be written again, effective after a power cycle; PWD and PACK stay writable) and/or
 * AUTHLIM (after that many wrong passwords the protected area locks for good). One ACCESS write, read-modify-write.
 */
class ConfigLockUseCase(private val confirmed: ConfirmedPlan) : TagOperation<SecurityReport> {
    override val kind: OperationKind = OperationKind.SECURITY

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<SecurityReport> =
        SecurityExecutor.run(session, confirmed, SecurityOperation.CONFIG_LOCK, phase)

    companion object {
        fun plan(state: SecurityState, request: ConfigLockRequest): Outcome<SecurityPlan, SecurityFailure> {
            val model = state.model
            val config = state.config
            if (!request.lockConfig && request.authLimit == 0) return Outcome.Err(SecurityFailure.NothingToChange)
            if (config.cfgLock) return Outcome.Err(SecurityFailure.ConfigLocked)
            val current = if (state.isPasswordProtected) request.currentPassword ?: return Outcome.Err(SecurityFailure.PasswordRequired) else null

            var updated = config
            if (request.authLimit > 0) updated = updated.withAuthLimit(request.authLimit)
            if (request.lockConfig) updated = updated.withCfgLock(true)
            val effects = buildSet {
                if (request.lockConfig) add(IrreversibleEffect.CONFIG_PAGES_LOCKED_FOREVER)
                if (request.authLimit > 0) add(IrreversibleEffect.AUTH_ATTEMPTS_LIMITED)
            }
            return Outcome.Ok(
                SecurityPlan(
                    operation = SecurityOperation.CONFIG_LOCK,
                    uid = state.info.uid,
                    model = model,
                    steps = listOfNotNull(current?.let { PlanStep.Authenticate(it.bytes) }) +
                        PlanStep.WritePage(model.cfg1, old = config.cfg1, new = updated.cfg1),
                    effects = effects,
                    checks = listOf(Check.PageEquals(model.cfg1, updated.cfg1)),
                ),
            )
        }
    }
}
