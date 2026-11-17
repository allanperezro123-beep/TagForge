package com.tagforge.app.ui.text

import com.tagforge.app.R
import com.tagforge.domain.security.PasswordPageState
import com.tagforge.domain.security.ProtectionState
import com.tagforge.domain.security.SecurityFailure
import com.tagforge.domain.security.SecurityOperation

/** The text for every [SecurityFailure]. Partial failures say what state the tag may be in and what to do. */
object SecurityErrorText {
    fun of(failure: SecurityFailure): ErrorText = when (failure) {
        SecurityFailure.UidMismatch -> ErrorText(R.string.error_sec_uid_mismatch)
        SecurityFailure.ConfigLocked -> ErrorText(R.string.error_sec_config_locked)
        SecurityFailure.Unremovable -> ErrorText(R.string.error_sec_unremovable)
        SecurityFailure.AuthExhausted -> ErrorText(R.string.error_sec_auth_exhausted)
        SecurityFailure.AuthOutcomeUnknown -> ErrorText(R.string.error_sec_auth_outcome_unknown)
        SecurityFailure.PackMismatch -> ErrorText(R.string.error_sec_pack_mismatch)
        SecurityFailure.AdvancedRequired -> ErrorText(R.string.error_sec_advanced_required)
        SecurityFailure.UnsupportedChip -> ErrorText(R.string.error_sec_unsupported_chip)
        SecurityFailure.NotProtected -> ErrorText(R.string.error_sec_not_protected)
        SecurityFailure.PasswordRequired -> ErrorText(R.string.error_sec_password_required)
        SecurityFailure.Auth0OutOfRange -> ErrorText(R.string.error_sec_auth0_range)
        SecurityFailure.StateChanged -> ErrorText(R.string.error_sec_state_changed)
        SecurityFailure.NothingToChange -> ErrorText(R.string.error_sec_nothing_to_change)
        SecurityFailure.WrongPlan -> ErrorText(R.string.error_sec_wrong_plan)
        SecurityFailure.CannotMakeReadOnly -> ErrorText(R.string.error_sec_cannot_make_read_only)
        is SecurityFailure.NotVerified -> ErrorText(R.string.error_sec_not_verified)
        is SecurityFailure.Partial -> ErrorText(partialMessage(failure), listOf(failure.completedWrites, failure.totalWrites))
    }

    /** Set and remove password say what the tag looks like now (protection, password page); the rest keep one general text. */
    private fun partialMessage(partial: SecurityFailure.Partial): Int = when (partial.operation) {
        SecurityOperation.SET_PASSWORD -> when (partial.protection) {
            ProtectionState.PROTECTED -> R.string.error_sec_partial_set_protected
            ProtectionState.NOT_PROTECTED -> when (partial.passwordPage) {
                PasswordPageState.CHANGED -> R.string.error_sec_partial_set_not_protected_changed
                PasswordPageState.UNCHANGED -> R.string.error_sec_partial_set_not_protected
                PasswordPageState.UNKNOWN -> R.string.error_sec_partial_set_not_protected_maybe
            }
            ProtectionState.UNKNOWN -> R.string.error_sec_partial_set_password
        }
        SecurityOperation.REMOVE_PASSWORD -> when (partial.protection) {
            ProtectionState.NOT_PROTECTED -> R.string.error_sec_partial_remove_open
            ProtectionState.PROTECTED -> R.string.error_sec_partial_remove_still_protected
            ProtectionState.UNKNOWN -> R.string.error_sec_partial_remove
        }
        SecurityOperation.LOCK -> R.string.error_sec_partial_lock
        SecurityOperation.CONFIG_LOCK, SecurityOperation.RAW_WRITE -> R.string.error_sec_partial_other
    }
}
