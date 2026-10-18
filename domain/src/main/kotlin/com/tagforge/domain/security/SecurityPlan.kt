package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagMemoryMap
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ntag.Region

/** The NS operations that change a tag for good (or can brick it). Every one goes through [SecurityPlan] (NS-01). */
enum class SecurityOperation { SET_PASSWORD, REMOVE_PASSWORD, CONFIG_LOCK, LOCK, RAW_WRITE }

/** What the user is told cannot be undone. The UI maps each value to a localized warning. */
enum class IrreversibleEffect {
    /** Reading and writing from AUTH0 on needs the password; a lost password leaves the pages unreachable. */
    PASSWORD_PROTECTS_TAG,

    /** Writing from AUTH0 on needs the password (PROT = 0); reading stays open. */
    WRITE_PROTECTED_ONLY,

    /** CFGLCK: CFG0 and CFG1 can never be written again (active after a power cycle). PWD and PACK stay writable. */
    CONFIG_PAGES_LOCKED_FOREVER,

    /** AUTHLIM > 0: after that many wrong passwords the protected area locks permanently, even for the right password. */
    AUTH_ATTEMPTS_LIMITED,

    /** Lock bits are OR-ed: a bit that is set can never be cleared. */
    LOCK_BITS_NEVER_CLEARED,

    /** The capability container turns read-only for good. */
    TAG_READ_ONLY_FOREVER,

    /** A hand-written frame to a lock, configuration or password page. */
    RAW_RESERVED_PAGE_WRITE,
}

/** One step of a plan. Page writes are exact WRITE frames; [MakeReadOnly] is the platform's `Ndef.makeReadOnly`. */
sealed interface PlanStep {
    /** [old] is null when the chip never reveals it (PWD, PACK) or the page was not read. */
    data class WritePage(val page: Int, val old: Bytes?, val new: Bytes) : PlanStep {
        val command: Bytes get() = NtagCommands.write(page, new)
    }

    /** PWD_AUTH with [password]; used first by plans that need an authenticated session. */
    data class Authenticate(val password: Bytes) : PlanStep {
        val command: Bytes get() = NtagCommands.pwdAuth(password)
    }

    data object MakeReadOnly : PlanStep

    /** A hand-written frame that is not a plain page write (the raw console's `A0 <page>`). */
    data class Raw(val frame: Bytes) : PlanStep
}

/** One row of the dry-run preview. [oldHex] is null when the chip does not reveal the old content. */
data class DryRunLine(
    val page: Int,
    val region: Region?,
    val commandHex: String,
    val oldHex: String?,
    val newHex: String,
    val oldHidden: Boolean,
)

/**
 * Everything an irreversible operation will do, decided BEFORE the tag is touched (NS-01).
 *
 * Built from a read of the tag, shown to the user as a dry-run, and turned into a [ConfirmedPlan] only by typing the
 * last 4 hex characters of the UID. Execution refuses any other tag.
 */
data class SecurityPlan(
    val operation: SecurityOperation,
    val uid: Bytes,
    val model: NtagModel,
    val steps: List<PlanStep>,
    val effects: Set<IrreversibleEffect>,
    /** What to verify by reading the tag afterwards. */
    val checks: List<Check> = emptyList(),
) {
    /** The frames, in the order they are sent. */
    val commands: List<Bytes>
        get() = steps.mapNotNull {
            when (it) {
                is PlanStep.WritePage -> it.command
                is PlanStep.Authenticate -> it.command
                is PlanStep.Raw -> it.frame
                PlanStep.MakeReadOnly -> null
            }
        }

    fun dryRun(): List<DryRunLine> = steps.mapNotNull { step ->
        when (step) {
            is PlanStep.WritePage -> writeLine(step)
            is PlanStep.Raw -> DryRunLine(
                page = step.frame.takeIf { it.size > 1 }?.get(1) ?: -1,
                region = step.frame.takeIf { it.size > 1 }?.let { NtagMemoryMap.regionsOf(model, it[1]).firstOrNull() },
                commandHex = step.frame.toHex(" "),
                oldHex = null,
                newHex = "",
                oldHidden = true,
            )
            is PlanStep.Authenticate, PlanStep.MakeReadOnly -> null
        }
    }

    private fun writeLine(step: PlanStep.WritePage): DryRunLine {
        val region = NtagMemoryMap.regionsOf(model, step.page).firstOrNull()
        val hidden = region?.hiddenByChip == true || step.old == null
        return DryRunLine(
            page = step.page,
            region = region,
            commandHex = step.command.toHex(" "),
            oldHex = if (hidden) null else step.old?.toHex(" "),
            newHex = step.new.toHex(" "),
            oldHidden = hidden,
        )
    }

    fun isForTag(tappedUid: Bytes): Boolean = tappedUid == uid

    /** The only way to get a [ConfirmedPlan]: [typed] must be the last 4 hex characters of [uid]. */
    fun confirm(typed: String): ConfirmedPlan? = if (UidConfirmation.matches(uid, typed)) ConfirmedPlan(this) else null
}

/** Proof that the user typed the confirmation for exactly this plan. Only [SecurityPlan.confirm] creates one. */
class ConfirmedPlan internal constructor(val plan: SecurityPlan)
