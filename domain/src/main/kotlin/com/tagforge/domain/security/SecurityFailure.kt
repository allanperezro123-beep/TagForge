package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.tag.TagError

/** What to prove by reading the tag after a plan ran. Page contents of PWD and PACK are never read back (the chip hides them). */
sealed interface Check {
    /** The page now holds exactly [expected]. */
    data class PageEquals(val page: Int, val expected: Bytes) : Check

    /** Every bit of [bits] is set in the page (lock bits are OR-ed, so equality would be wrong). */
    data class PageHasBits(val page: Int, val bits: Bytes) : Check

    /** PWD_AUTH with [password] succeeds and answers [pack]. Proves the password, since PWD itself reads as 00. */
    data class AuthWorks(val password: Bytes, val pack: Bytes) : Check

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

    /** The tag is password protected and the request did not carry the current password. */
    data object PasswordRequired : SecurityFailure

    /** AUTH0 must lie between the first user page and the PWD page, so the password page itself is protected. */
    data object Auth0OutOfRange : SecurityFailure

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
    ) : SecurityFailure

    /** Every write went through but the read-back did not show the expected result. */
    data class NotVerified(val check: Check) : SecurityFailure
}
