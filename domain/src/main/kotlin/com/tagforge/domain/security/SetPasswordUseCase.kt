package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation

/** What the user wants for password protection (NS-02). AUTHLIM is deliberately NOT here: it is a separate step (NS-05). */
data class PasswordRequest(
    val password: Password,
    val pack: Bytes,
    val auth0: Int,
    /** PROT: true = reads and writes need the password; false = only writes do. */
    val protectReads: Boolean = true,
    /** Required when the tag is already protected. */
    val currentPassword: Password? = null,
) {
    init {
        require(pack.size == NtagCommands.PACK_SIZE) { "PACK is ${NtagCommands.PACK_SIZE} bytes" }
    }
}

class SetPasswordUseCase(private val confirmed: ConfirmedPlan) : TagOperation<SecurityReport> {
    override val kind: OperationKind = OperationKind.SECURITY

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<SecurityReport> =
        SecurityExecutor.run(session, confirmed, phase)

    companion object {
        private val RFUI_PAIR = Bytes.of(0x00, 0x00)

        /**
         * Reads nothing and writes nothing: turns the tag's current [state] into the exact plan (NS-02). Order: PWD, PACK,
         * CFG1 (ACCESS) and CFG0 (AUTH0) LAST, so an interrupted run never leaves protection active half-configured.
         */
        fun plan(state: SecurityState, request: PasswordRequest): Outcome<SecurityPlan, SecurityFailure> {
            val model = state.model
            val config = state.config
            if (request.auth0 !in model.userStart..model.pwd) return Outcome.Err(SecurityFailure.Auth0OutOfRange)
            if (config.cfgLock) return Outcome.Err(SecurityFailure.ConfigLocked)
            val current = if (state.isPasswordProtected) request.currentPassword ?: return Outcome.Err(SecurityFailure.PasswordRequired) else null

            val withAccess = config.withProt(request.protectReads)
            val withAuth0 = config.withAuth0(request.auth0)
            val steps = listOfNotNull(current?.let { PlanStep.Authenticate(it.bytes) }) + listOf(
                PlanStep.WritePage(model.pwd, old = null, new = request.password.bytes),
                PlanStep.WritePage(model.pack, old = null, new = request.pack + RFUI_PAIR),
                PlanStep.WritePage(model.cfg1, old = config.cfg1, new = withAccess.cfg1),
                PlanStep.WritePage(model.cfg0, old = config.cfg0, new = withAuth0.cfg0),
            )
            return Outcome.Ok(
                SecurityPlan(
                    operation = SecurityOperation.SET_PASSWORD,
                    uid = state.info.uid,
                    model = model,
                    steps = steps,
                    effects = setOf(if (request.protectReads) IrreversibleEffect.PASSWORD_PROTECTS_TAG else IrreversibleEffect.WRITE_PROTECTED_ONLY),
                    checks = listOf(
                        Check.AuthWorks(request.password.bytes, request.pack),
                        Check.PageEquals(model.cfg1, withAccess.cfg1),
                        Check.PageEquals(model.cfg0, withAuth0.cfg0),
                    ),
                ),
            )
        }
    }
}
