package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation

/** How far a permanent lock goes. Everything but [NDEF_READ_ONLY] writes lock bits by hand and needs Advanced mode. */
enum class LockScope { NDEF_READ_ONLY, CC_ONLY, STATIC_PAGES, DYNAMIC_PAGES, FULL }

data class LockRequest(val scope: LockScope, val advanced: Boolean, val currentPassword: Password? = null)

/**
 * Permanent read-only locks (NS-06). `Ndef.makeReadOnly` when the platform offers it; otherwise raw writes to the CC, the
 * dynamic lock bytes (masks from [DynamicLockTable]) and the static lock bytes, in that order (the static bytes lock the CC
 * itself, so they go last). Lock bits are OR-ed by the chip and can never be cleared.
 */
class LockTagUseCase(private val confirmed: ConfirmedPlan) : TagOperation<SecurityReport> {
    override val kind: OperationKind = OperationKind.SECURITY

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<SecurityReport> =
        SecurityExecutor.run(session, confirmed, SecurityOperation.LOCK, phase)

    companion object {
        private const val CC_PAGE = 3
        private const val STATIC_LOCK_PAGE = 2
        private val CC_READ_ONLY = Bytes.of(0x00, 0x00, 0x00, 0x0F)
        private val STATIC_PAGES_4_TO_15 = Bytes.of(0x00, 0x00, 0xF0, 0xFF)
        private val STATIC_PAGES_AND_CC = Bytes.of(0x00, 0x00, 0xF8, 0xFF)

        fun plan(state: SecurityState, request: LockRequest): Outcome<SecurityPlan, SecurityFailure> {
            if (request.scope == LockScope.NDEF_READ_ONLY) return platformPlan(state)
            if (!request.advanced) return Outcome.Err(SecurityFailure.AdvancedRequired)
            val current = if (state.isPasswordProtected) request.currentPassword ?: return Outcome.Err(SecurityFailure.PasswordRequired) else null

            val writes = rawWrites(state, request.scope).filterNot { alreadySet(it.old!!, it.new) }
            if (writes.isEmpty()) return Outcome.Err(SecurityFailure.NothingToChange)
            val effects = buildSet {
                add(IrreversibleEffect.LOCK_BITS_NEVER_CLEARED)
                if (writes.any { it.page == CC_PAGE }) add(IrreversibleEffect.TAG_READ_ONLY_FOREVER)
            }
            return Outcome.Ok(
                SecurityPlan(
                    operation = SecurityOperation.LOCK,
                    uid = state.info.uid,
                    model = state.model,
                    steps = listOfNotNull(current?.let { PlanStep.Authenticate(it.bytes) }) + writes,
                    effects = effects,
                    checks = writes.map { Check.PageHasBits(it.page, it.new) },
                ),
            )
        }

        private fun platformPlan(state: SecurityState): Outcome<SecurityPlan, SecurityFailure> {
            val ndef = state.info.ndef
            if (ndef == null || !ndef.canMakeReadOnly) return Outcome.Err(SecurityFailure.CannotMakeReadOnly)
            if (!ndef.isWritable) return Outcome.Err(SecurityFailure.NothingToChange)
            return Outcome.Ok(
                SecurityPlan(
                    operation = SecurityOperation.LOCK,
                    uid = state.info.uid,
                    model = state.model,
                    steps = listOf(PlanStep.MakeReadOnly),
                    effects = setOf(IrreversibleEffect.LOCK_BITS_NEVER_CLEARED, IrreversibleEffect.TAG_READ_ONLY_FOREVER),
                    checks = listOf(Check.NdefReadOnly),
                ),
            )
        }

        /** Order matters: CC first, then dynamic, then the static bytes (which freeze the CC) last. */
        private fun rawWrites(state: SecurityState, scope: LockScope): List<PlanStep.WritePage> {
            val model = state.model
            val cc = PlanStep.WritePage(CC_PAGE, state.cc, CC_READ_ONLY)
            val dynamicPayload = DynamicLockTable.layout(model).commandForAll().slice(2)
            val dynamic = PlanStep.WritePage(model.dynLock, state.dynamicLockPage, dynamicPayload)
            fun static(bits: Bytes) = PlanStep.WritePage(STATIC_LOCK_PAGE, state.page2, bits)
            return when (scope) {
                LockScope.CC_ONLY -> listOf(cc)
                LockScope.STATIC_PAGES -> listOf(static(STATIC_PAGES_4_TO_15))
                LockScope.DYNAMIC_PAGES -> listOf(dynamic)
                LockScope.FULL -> listOf(cc, dynamic, static(STATIC_PAGES_AND_CC))
                LockScope.NDEF_READ_ONLY -> emptyList()
            }
        }

        private fun alreadySet(old: Bytes, bits: Bytes): Boolean =
            (0 until NtagCommands.PAGE_SIZE).all { old[it] and bits[it] == bits[it] }
    }
}
