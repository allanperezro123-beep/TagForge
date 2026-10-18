package com.tagforge.app.feature.tools.security

import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.security.ProbedTag
import com.tagforge.domain.security.RawReply
import com.tagforge.domain.security.SecurityPlan
import com.tagforge.domain.security.UidConfirmation
import com.tagforge.domain.tag.TagError

/** How one console line ended. */
sealed interface ConsoleOutcome {
    data class Reply(val reply: RawReply) : ConsoleOutcome

    /** "Tag lost": the console is ready for the next frame. */
    data object TagLost : ConsoleOutcome

    data class Failed(val error: TagError) : ConsoleOutcome
}

data class ConsoleEntry(val frameHex: String, val outcome: ConsoleOutcome)

/** A frame that is waiting for the user before it may be sent. */
sealed interface ConsolePending {
    val frame: Bytes

    /** A user-page write (or an unknown frame): one tap on Confirm. */
    data class Light(override val frame: Bytes, val tag: ProbedTag) : ConsolePending

    /** A write to a reserved page: the NS-01 dry-run and the typed UID. */
    data class Typed(override val frame: Bytes, val plan: SecurityPlan, val typed: String = "") : ConsolePending

    /** A reserved write whose tag configuration could not be read, so no plan can be built. */
    data class Unplannable(override val frame: Bytes) : ConsolePending
}

sealed interface ConsoleEvent {
    data object TagDetected : ConsoleEvent
    data object Succeeded : ConsoleEvent
    data object Failed : ConsoleEvent
}

data class RawConsoleUiState(
    val availability: NfcAvailability = NfcAvailability.ENABLED,
    val advanced: Boolean = false,
    val acknowledged: Boolean = false,
    val hexInput: String = "",
    val hexError: Boolean = false,
    val log: List<ConsoleEntry> = emptyList(),
    val pending: ConsolePending? = null,
    val sheet: SecuritySheet? = null,
) {
    val needsAck: Boolean get() = !acknowledged

    val canSend: Boolean get() = advanced && availability == NfcAvailability.ENABLED && sheet == null && pending == null

    val canConfirmTyped: Boolean
        get() = (pending as? ConsolePending.Typed)?.let { UidConfirmation.matches(it.plan.uid, it.typed) } == true
}
