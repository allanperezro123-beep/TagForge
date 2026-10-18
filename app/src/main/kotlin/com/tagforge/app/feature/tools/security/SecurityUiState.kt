package com.tagforge.app.feature.tools.security

import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.security.AuthLimitInfo
import com.tagforge.domain.security.LockScope
import com.tagforge.domain.security.SecurityFailure
import com.tagforge.domain.security.SecurityPlan
import com.tagforge.domain.security.UidConfirmation
import com.tagforge.domain.tag.TagError

enum class SecurityAction { SET_PASSWORD, REMOVE_PASSWORD, CONFIG_LOCK, LOCK, UNLOCK }

/** Where the user is: choosing, filling the form, or looking at the dry-run that waits for the typed UID. */
enum class SecurityStep { CHOOSE, FORM, DRY_RUN }

/** A tap is for reading the tag (nothing is written) or for executing the confirmed plan. */
enum class SecurityStage { READ, EXECUTE }

/** What is wrong with the form, found BEFORE any tag is touched. */
enum class FormProblem { PASSWORD_INVALID, PACK_INVALID, STORED_NOT_ACKNOWLEDGED, CURRENT_PASSWORD_INVALID, NOTHING_SELECTED }

/**
 * Everything the user types. Secrets live only here, in memory: they are wiped after a run and never reach the history.
 */
data class SecurityForm(
    val passwordHex: String = "",
    val packHex: String = "",
    /** First protected page; defaults to the first user page. */
    val auth0: Int = 4,
    /** PROT: reads need the password too. */
    val protectReads: Boolean = true,
    /** "I stored it": the write button stays disabled until the shown password was saved. */
    val storedAck: Boolean = false,
    val rememberInKeystore: Boolean = false,
    val currentPasswordHex: String = "",
    val lockConfig: Boolean = false,
    val authLimit: Int = 0,
    val lockScope: LockScope = LockScope.NDEF_READ_ONLY,
)

sealed interface SecuritySheet {
    data class Waiting(val stage: SecurityStage) : SecuritySheet
    data class Working(val stage: SecurityStage, val phase: OperationPhase) : SecuritySheet

    /** [pack] is the PACK the tag answered after an unlock. */
    data class Success(val action: SecurityAction, val pack: Bytes? = null) : SecuritySheet

    data class Failure(val error: TagError, val canRetry: Boolean) : SecuritySheet
}

sealed interface SecurityEvent {
    data object TagDetected : SecurityEvent
    data object Succeeded : SecurityEvent
    data object Failed : SecurityEvent
}

data class SecurityUiState(
    val availability: NfcAvailability = NfcAvailability.ENABLED,
    val advanced: Boolean = false,
    val action: SecurityAction? = null,
    val step: SecurityStep = SecurityStep.CHOOSE,
    val form: SecurityForm = SecurityForm(),
    val problem: FormProblem? = null,
    /** The tag was read but the operation cannot be planned for it (config locked, nothing to change...). */
    val failure: SecurityFailure? = null,
    val plan: SecurityPlan? = null,
    val typed: String = "",
    val understood: Boolean = false,
    val sheet: SecuritySheet? = null,
    /** Unlock only: the attempt limit read from the tag; null until read. */
    val authLimit: AuthLimitInfo? = null,
    val limitUnderstood: Boolean = false,
) {
    val canStart: Boolean get() = availability == NfcAvailability.ENABLED

    /** The warning that must be acknowledged on top of the typed UID: anything that cannot be undone. */
    val needsUnderstood: Boolean get() = plan?.effects?.isNotEmpty() == true

    val canConfirm: Boolean
        get() = plan != null && UidConfirmation.matches(plan.uid, typed) && (!needsUnderstood || understood)

    /**
     * A password can only be typed (and so only be tried against the tag) once its attempt limit was READ and, unless that
     * limit is a known 0, acknowledged. An unread or unreadable limit is never taken for 0: wrong guesses may count toward
     * a permanent lock (W2).
     */
    val passwordFieldEnabled: Boolean
        get() = authLimit != null && (authLimit.authLimit == 0 || limitUnderstood)

    /** The flow will send a password to the tag on its first tap. */
    val willSendPassword: Boolean
        get() = action == SecurityAction.UNLOCK || action == SecurityAction.REMOVE_PASSWORD || form.currentPasswordHex.isNotBlank()

    /** "Next" is allowed: a flow that sends a password needs the attempt limit settled first. */
    val canGoNext: Boolean get() = !willSendPassword || passwordFieldEnabled
}
