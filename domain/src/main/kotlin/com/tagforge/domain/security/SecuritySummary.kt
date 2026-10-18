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
        if (partial != null) return "$what: interrupted after ${partial.completedWrites} of ${partial.totalWrites} writes; ${consequence(operation)}"
        return "$what: failed (${reason(error)})"
    }

    private fun consequence(operation: SecurityOperation): String = when (operation) {
        SecurityOperation.SET_PASSWORD -> "protection may NOT be active, retry with the same password"
        SecurityOperation.REMOVE_PASSWORD -> "protection may be partly removed, authenticate and retry"
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
            SecurityFailure.AdvancedRequired -> "Advanced mode required"
            SecurityFailure.StateChanged -> "tag changed since planning"
            is SecurityFailure.NotVerified -> "not verified"
            else -> "refused"
        }
        else -> "error"
    }
}
