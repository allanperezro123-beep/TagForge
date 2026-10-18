package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Hex
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel

enum class ReservedReason {
    STATIC_LOCK, CC, DYNAMIC_LOCK, CFG0, CFG1, PWD, PACK,

    /** PWD_AUTH on a tag with AUTHLIM > 0: a wrong password counts toward a permanent lockout. */
    AUTH_ATTEMPT_LIMITED,

    /** PWD_AUTH when AUTHLIM could not be read (configuration behind the password). */
    AUTH_LIMIT_UNKNOWN,

    /** A write when the chip model is not known, so reserved pages cannot be told from user pages. */
    CHIP_UNKNOWN,
}

enum class InvalidReason { EMPTY, BAD_LENGTH, PAGE_OUT_OF_RANGE }

sealed interface RawClass {
    /** Runs without any confirmation. */
    data object ReadClass : RawClass

    /** Writes a user page (or is an unknown frame): needs the lightweight confirm. */
    data object UserWrite : RawClass

    /** Goes through NS-01: dry-run, typed UID confirmation, UID re-check. [page] is null for PWD_AUTH. */
    data class Reserved(val page: Int?, val reason: ReservedReason) : RawClass

    data class Invalid(val reason: InvalidReason) : RawClass
}

sealed interface RawRefusal {
    data object AdvancedRequired : RawRefusal
    data object NeedsConfirmation : RawRefusal
    data object NeedsTypedConfirmation : RawRefusal
    data class Invalid(val reason: InvalidReason) : RawRefusal

    /** A typed-confirmation plan was asked for a frame that does not need one. */
    data object NotReserved : RawRefusal
}

/** The tag a console probe looked at: what a lightweight confirm was given for (W1). */
data class ProbedTag(val uid: Bytes, val model: NtagModel?)

/**
 * A frame the guard let through, with the typed confirmation it needed (if any). Only [RawCommandGuard.authorize] builds one.
 * [boundTo] is set for a lightweight-confirmed user write: it may only be sent to that very tag.
 */
class RawCommand internal constructor(val frame: Bytes, val plan: ConfirmedPlan?, val boundTo: ProbedTag? = null)

/** Decides what a hand-written frame needs before it may reach the tag (NS-08). */
object RawCommandGuard {
    private val READ_LENGTHS = mapOf(
        NtagCommands.READ to 2, NtagCommands.FAST_READ to 3, NtagCommands.GET_VERSION to 1,
        NtagCommands.READ_CNT to 2, NtagCommands.READ_SIG to 2,
    )
    private const val WRITE_LENGTH = 6
    private const val COMPAT_WRITE_LENGTH = 2
    private const val AUTH_LENGTH = 5

    /** Hex with optional spaces or colons; null when it is empty or not a whole number of bytes. */
    fun parseHex(text: String): Bytes? = Hex.parse(text)?.takeIf { !it.isEmpty() }

    fun classify(frame: Bytes, model: NtagModel?, authLimit: Int?): RawClass {
        if (frame.isEmpty()) return RawClass.Invalid(InvalidReason.EMPTY)
        val opcode = frame[0]
        READ_LENGTHS[opcode]?.let { return if (frame.size == it) RawClass.ReadClass else RawClass.Invalid(InvalidReason.BAD_LENGTH) }
        return when (opcode) {
            NtagCommands.WRITE -> classifyWrite(frame, WRITE_LENGTH, model)
            NtagCommands.COMPATIBILITY_WRITE -> classifyWrite(frame, COMPAT_WRITE_LENGTH, model)
            NtagCommands.PWD_AUTH -> classifyAuth(frame, authLimit)
            else -> RawClass.UserWrite
        }
    }

    private fun classifyAuth(frame: Bytes, authLimit: Int?): RawClass = when {
        frame.size != AUTH_LENGTH -> RawClass.Invalid(InvalidReason.BAD_LENGTH)
        authLimit == null -> RawClass.Reserved(null, ReservedReason.AUTH_LIMIT_UNKNOWN)
        authLimit > 0 -> RawClass.Reserved(null, ReservedReason.AUTH_ATTEMPT_LIMITED)
        else -> RawClass.ReadClass
    }

    private fun classifyWrite(frame: Bytes, length: Int, model: NtagModel?): RawClass {
        if (frame.size != length) return RawClass.Invalid(InvalidReason.BAD_LENGTH)
        val page = frame[1]
        if (model == null) return RawClass.Reserved(page, ReservedReason.CHIP_UNKNOWN)
        if (page > model.lastPage) return RawClass.Invalid(InvalidReason.PAGE_OUT_OF_RANGE)
        val reason = when (page) {
            2 -> ReservedReason.STATIC_LOCK
            3 -> ReservedReason.CC
            model.dynLock -> ReservedReason.DYNAMIC_LOCK
            model.cfg0 -> ReservedReason.CFG0
            model.cfg1 -> ReservedReason.CFG1
            model.pwd -> ReservedReason.PWD
            model.pack -> ReservedReason.PACK
            else -> return RawClass.UserWrite
        }
        return RawClass.Reserved(page, reason)
    }

    /**
     * The only way to get a [RawCommand]. [plan] is the typed confirmation of a reserved frame (built by
     * [RawConsoleUseCase.plan]); it must be for exactly this frame.
     */
    fun authorize(
        frame: Bytes,
        model: NtagModel?,
        authLimit: Int?,
        advanced: Boolean,
        lightweightFor: ProbedTag? = null,
        plan: ConfirmedPlan? = null,
    ): Outcome<RawCommand, RawRefusal> {
        if (!advanced) return Outcome.Err(RawRefusal.AdvancedRequired)
        return when (val kind = classify(frame, model, authLimit)) {
            is RawClass.Invalid -> Outcome.Err(RawRefusal.Invalid(kind.reason))
            RawClass.ReadClass -> Outcome.Ok(RawCommand(frame, null))
            RawClass.UserWrite ->
                if (lightweightFor != null) Outcome.Ok(RawCommand(frame, null, lightweightFor)) else Outcome.Err(RawRefusal.NeedsConfirmation)
            is RawClass.Reserved ->
                if (plan != null && plan.plan.operation == SecurityOperation.RAW_WRITE && plan.plan.commands == listOf(frame)) {
                    Outcome.Ok(RawCommand(frame, plan))
                } else {
                    Outcome.Err(RawRefusal.NeedsTypedConfirmation)
                }
        }
    }
}
