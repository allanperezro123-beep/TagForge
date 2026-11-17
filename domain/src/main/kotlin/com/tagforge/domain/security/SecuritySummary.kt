package com.tagforge.domain.security

import com.tagforge.domain.tag.TagError

/**
 * The one-line description an NS operation leaves in the history (HT-01). It never contains a password, a PACK or a frame:
 * only what was attempted and how it ended.
 */
object SecuritySummary {

    fun of(operation: SecurityOperation, error: TagError?): String {
        val what = when (operation) {
            SecurityOperation.SET_PASSWORD -> "Password protection set"
            SecurityOperation.REMOVE_PASSWORD -> "Password protection removed"
            SecurityOperation.CONFIG_LOCK -> "Configuration locked or attempt limit set"
            SecurityOperation.LOCK -> "Permanent lock applied"
            SecurityOperation.RAW_WRITE -> "Raw write to a reserved page"
        }
        if (error == null) return what
        val partial = (error as? TagError.Security)?.failure as? SecurityFailure.Partial
        if (partial != null) return "$what: interrupted after ${partial.completedWrites} of ${partial.totalWrites} writes; ${consequence(partial)}"
        return "$what: failed (${reason(error)})"
    }

    private fun consequence(partial: SecurityFailure.Partial): String = when (partial.operation) {
        SecurityOperation.SET_PASSWORD -> when (partial.protection) {
            ProtectionState.NOT_PROTECTED ->
                if (partial.passwordPage == PasswordPageState.UNCHANGED) "protection is NOT active, password unchanged, run it again"
                else "protection is NOT active, password page already rewritten, run it again with the same password"
            ProtectionState.PROTECTED -> "protection is active, check which password the tag accepts"
            ProtectionState.UNKNOWN -> "protection state unknown, re-scan to read the real state"
        }
        SecurityOperation.REMOVE_PASSWORD -> when (partial.protection) {
            ProtectionState.NOT_PROTECTED -> "protection removed, stored password not reset"
            ProtectionState.PROTECTED -> "protection still active, authenticate and retry"
            ProtectionState.UNKNOWN -> "protection state unknown, re-scan to read the real state"
        }
        SecurityOperation.LOCK -> "the lock may be partially applied, re-scan to read the real state"
        SecurityOperation.CONFIG_LOCK, SecurityOperation.RAW_WRITE -> "the change may be partially applied, re-scan to check"
    }

    private fun reason(error: TagError): String = when (error) {
        TagError.ReadOnly -> "read-only"
        TagError.TagLost -> "tag lost"
        TagError.AuthRequired -> "password required"
        is TagError.AuthFailed -> "wrong password"
        is TagError.Nak -> "tag refused"
        is TagError.Security -> when (error.failure) {
            SecurityFailure.UidMismatch -> "UID mismatch"
            SecurityFailure.ConfigLocked, SecurityFailure.Unremovable -> "configuration locked"
            SecurityFailure.AuthExhausted -> "attempt limit reached"
            SecurityFailure.AuthOutcomeUnknown -> "link dropped after the password: the attempt may have counted"
            SecurityFailure.PackMismatch -> "tag answered another PACK: possible clone"
            SecurityFailure.AdvancedRequired -> "Advanced mode required"
            SecurityFailure.StateChanged -> "tag changed since planning"
            is SecurityFailure.NotVerified -> "not verified"
            else -> "refused"
        }
        else -> "error"
    }
}
