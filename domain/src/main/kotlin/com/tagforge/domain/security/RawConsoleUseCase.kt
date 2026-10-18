package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagResponses
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagTech

/** The 4-bit NAK codes of the datasheet (Table 23). */
enum class NakMeaning(val code: Int) {
    INVALID_ARGUMENT(0x0), PARITY_OR_CRC(0x1), COUNTER_OVERFLOW(0x4), EEPROM_WRITE(0x5),
    ;

    companion object {
        fun of(code: Int): NakMeaning? = entries.firstOrNull { it.code == code }
    }
}

sealed interface RawReply {
    data object Ack : RawReply
    data class Nak(val code: Int, val meaning: NakMeaning?) : RawReply
    data class Data(val bytes: Bytes) : RawReply

    /** The controller swallowed a 4-bit answer. */
    data object Empty : RawReply
}

data class RawResponse(val frame: Bytes, val reply: RawReply)

/** Sends one authorized frame (NS-08). Reserved frames run through the confirmed plan, so UID check and read-back apply. */
class RawConsoleUseCase(private val command: RawCommand) : TagOperation<RawResponse> {
    override val kind: OperationKind = OperationKind.SECURITY

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<RawResponse> {
        command.plan?.let { confirmed ->
            return when (val done = SecurityExecutor.run(session, confirmed, SecurityOperation.RAW_WRITE, phase)) {
                is Outcome.Err -> done
                is Outcome.Ok -> Outcome.Ok(RawResponse(command.frame, RawReply.Ack))
            }
        }
        phase(OperationPhase.CONNECTING)
        val t = session.transceiver(TagTech.NFC_A) ?: return Outcome.Err(TagError.Unsupported("nfc-a"))
        return when (val sent = t.transceive(command.frame)) {
            is Outcome.Err -> sent
            is Outcome.Ok -> Outcome.Ok(RawResponse(command.frame, decode(sent.value)))
        }
    }

    companion object {
        private const val ACK = 0x0A

        fun decode(response: Bytes): RawReply = when {
            response.isEmpty() -> RawReply.Empty
            response.size == 1 && NtagResponses.nakCode(response) != null -> RawReply.Nak(response[0], NakMeaning.of(response[0]))
            response.size == 1 && response[0] == ACK -> RawReply.Ack
            else -> RawReply.Data(response)
        }

        /** The NS-01 plan for a reserved frame, built from the tag's current [state]. */
        fun plan(frame: Bytes, state: SecurityState): Outcome<SecurityPlan, RawRefusal> {
            val kind = RawCommandGuard.classify(frame, state.model, state.config.authLimit)
            val reserved = kind as? RawClass.Reserved ?: return Outcome.Err(if (kind is RawClass.Invalid) RawRefusal.Invalid(kind.reason) else RawRefusal.NotReserved)
            val step = when {
                frame[0] == NtagCommands.PWD_AUTH -> PlanStep.Authenticate(frame.slice(1))
                frame[0] == NtagCommands.WRITE -> PlanStep.WritePage(frame[1], oldOf(state, frame[1]), frame.slice(2))
                else -> PlanStep.Raw(frame)
            }
            val effects = effectsOf(reserved, frame)
            val checks = (step as? PlanStep.WritePage)?.let { checksOf(reserved.reason, it) }.orEmpty()
            return Outcome.Ok(SecurityPlan(SecurityOperation.RAW_WRITE, state.info.uid, state.model, listOf(step), effects, checks))
        }

        private fun oldOf(state: SecurityState, page: Int): Bytes? = when (page) {
            2 -> state.page2
            3 -> state.cc
            state.model.dynLock -> state.dynamicLockPage
            state.model.cfg0 -> state.config.cfg0
            state.model.cfg1 -> state.config.cfg1
            else -> null
        }

        private fun effectsOf(reserved: RawClass.Reserved, frame: Bytes): Set<IrreversibleEffect> = buildSet {
            add(IrreversibleEffect.RAW_RESERVED_PAGE_WRITE)
            val payload = if (frame[0] == NtagCommands.WRITE) frame.slice(2) else Bytes.of(0, 0, 0, 0)
            when (reserved.reason) {
                ReservedReason.STATIC_LOCK, ReservedReason.DYNAMIC_LOCK -> add(IrreversibleEffect.LOCK_BITS_NEVER_CLEARED)
                ReservedReason.CC -> if (payload[3] != 0) add(IrreversibleEffect.TAG_READ_ONLY_FOREVER)
                ReservedReason.CFG1 -> {
                    if (payload[0] and NtagConfigBits.CFGLCK != 0) add(IrreversibleEffect.CONFIG_PAGES_LOCKED_FOREVER)
                    if (payload[0] and NtagConfigBits.AUTHLIM != 0) add(IrreversibleEffect.AUTH_ATTEMPTS_LIMITED)
                }
                ReservedReason.AUTH_ATTEMPT_LIMITED, ReservedReason.AUTH_LIMIT_UNKNOWN -> add(IrreversibleEffect.AUTH_ATTEMPTS_LIMITED)
                else -> Unit
            }
        }

        private fun checksOf(reason: ReservedReason, step: PlanStep.WritePage): List<Check> = when (reason) {
            ReservedReason.STATIC_LOCK, ReservedReason.DYNAMIC_LOCK, ReservedReason.CC -> listOf(Check.PageHasBits(step.page, step.new))
            ReservedReason.CFG0, ReservedReason.CFG1 -> listOf(Check.PageEquals(step.page, step.new))
            else -> emptyList()
        }
    }
}

private object NtagConfigBits {
    const val CFGLCK = 0x40
    const val AUTHLIM = 0x07
}
