package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.tag.TagError

/** What to prove by reading the tag after a plan ran. Page contents of PWD and PACK are never read back (the chip hides them). */
sealed interface Check {
    /** The page now holds exactly [expected]. */
    data class PageEquals(val page: Int, val expected: Bytes) : Check {
        override fun toString(): String = "PageEquals(page=$page, expected=****)"
    }

    /** Every bit of [bits] is set in the page (lock bits are OR-ed, so equality would be wrong). */
    data class PageHasBits(val page: Int, val bits: Bytes) : Check

    /** PWD_AUTH with [password] succeeds and answers [pack]. Proves the password, since PWD itself reads as 00. */
    data class AuthWorks(val password: Bytes, val pack: Bytes) : Check {
        override fun toString(): String = "AuthWorks(password=****, pack=****)"
    }

    /** The platform reports the NDEF area as no longer writable. */
    data object NdefReadOnly : Check
}

/** Failures specific to the NS operations. Wrapped in [TagError.Security] so they travel through the scan pipeline. */
sealed interface SecurityFailure {
    /** The tag presented is not the one the user confirmed: nothing was sent. */
    data object UidMismatch : SecurityFailure

    /** CFGLCK is active: the configuration pages cannot be written, refused before any write. */
    data object ConfigLocked : SecurityFailure

    /** CFGLCK is active and AUTH0 is set: protection can no longer be removed or changed. */
    data object Unremovable : SecurityFailure

    /** AUTHLIM was reached: the protected area is permanently locked, even for the correct password. */
    data object AuthExhausted : SecurityFailure

    /** The operation needs Advanced mode. */
    data object AdvancedRequired : SecurityFailure

    /** The chip is not an NTAG21x, or has no such feature. */
    data object UnsupportedChip : SecurityFailure

    /** There is no password protection to remove or authenticate against. */
    data object NotProtected : SecurityFailure

    /**
     * The link dropped right after a PWD_AUTH was sent: the tag may have refused the password and counted the attempt
     * against AUTHLIM, or accepted it. The password is never sent again automatically; the user must check it first.
     */
    data object AuthOutcomeUnknown : SecurityFailure

    /**
     * The tag accepted the remembered password but answered a PACK other than the one stored with it: it may be a clone of
     * the tag the user unlocked. The password is forgotten and nothing else is sent to this tag.
     */
    data object PackMismatch : SecurityFailure

    /** The tag is password protected and the request did not carry the current password. */
    data object PasswordRequired : SecurityFailure

    /** AUTH0 must lie between the first user page and CFG0, so the configuration (and the password pages after it) stay behind the password. */
    data object Auth0OutOfRange : SecurityFailure

    /** The platform says this tag cannot be made read-only through the NDEF API. */
    data object CannotMakeReadOnly : SecurityFailure

    /** Nothing was asked for (no CFGLCK and no attempt limit). */
    data object NothingToChange : SecurityFailure

    /** The confirmed plan belongs to another operation than the use case that was asked to run it. */
    data object WrongPlan : SecurityFailure

    /** The tag no longer holds what the plan was built from (read just before the first write): nothing was written. */
    data object StateChanged : SecurityFailure

    /**
     * The tag failed after [completedWrites] of [totalWrites] writes went through, or while a write was in flight:
     * the tag may be in an intermediate state. Never a success.
     */
    data class Partial(
        val operation: SecurityOperation,
        val cause: TagError,
        val completedWrites: Int,
        val totalWrites: Int,
        /** 0-based index (among the plan's writes) of the write that failed; null when every write went through and the read-back failed. */
        val failedStep: Int? = completedWrites,
        /** Whether password protection is active on the tag now, worked out from the plan: set-password writes AUTH0 last, removal first. */
        val protection: ProtectionState = ProtectionState.UNKNOWN,
        /** Whether the PWD page (the stored password) was rewritten. UNCHANGED for the operations whose plan never writes it. */
        val passwordPage: PasswordPageState = PasswordPageState.UNCHANGED,
    ) : SecurityFailure

    /** Every write went through but the read-back did not show the expected result. */
    data class NotVerified(val check: Check) : SecurityFailure
}

/** Is password protection active on the tag after an interrupted run? [UNKNOWN] when the write that decides it was in flight. */
enum class ProtectionState { NOT_PROTECTED, PROTECTED, UNKNOWN }

/** Was the PWD page rewritten? [UNKNOWN] when that very write was in flight when the link dropped. */
enum class PasswordPageState { UNCHANGED, CHANGED, UNKNOWN }
