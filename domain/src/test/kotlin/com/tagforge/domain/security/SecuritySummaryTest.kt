package com.tagforge.domain.security

import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryOutcome
import com.tagforge.domain.tag.TagError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the history keeps about an NS operation: what was attempted and how it ended. Never a password, never a frame. */
class SecuritySummaryTest {

    @Test
    fun eachOperationHasItsOwnWording() {
        assertEquals("Password protection set", SecuritySummary.of(SecurityOperation.SET_PASSWORD, null))
        assertEquals("Password protection removed", SecuritySummary.of(SecurityOperation.REMOVE_PASSWORD, null))
        assertEquals("Configuration locked or attempt limit set", SecuritySummary.of(SecurityOperation.CONFIG_LOCK, null))
        assertEquals("Permanent lock applied", SecuritySummary.of(SecurityOperation.LOCK, null))
        assertEquals("Raw write to a reserved page", SecurityOperation.RAW_WRITE.let { SecuritySummary.of(it, null) })
    }

    @Test
    fun aPartialFailureSaysHowFarItGotAndWhetherProtectionIsActive() {
        fun summary(protection: ProtectionState, page: PasswordPageState) = SecuritySummary.of(
            SecurityOperation.SET_PASSWORD,
            TagError.Security(SecurityFailure.Partial(SecurityOperation.SET_PASSWORD, TagError.TagLost, 1, 4, 1, protection, page)),
        )

        assertTrue(summary(ProtectionState.NOT_PROTECTED, PasswordPageState.CHANGED).contains("interrupted after 1 of 4 writes"))
        assertTrue(summary(ProtectionState.NOT_PROTECTED, PasswordPageState.CHANGED).contains("protection is NOT active, password page already rewritten"))
        assertTrue(summary(ProtectionState.NOT_PROTECTED, PasswordPageState.UNCHANGED).contains("protection is NOT active, password unchanged"))
        assertTrue(summary(ProtectionState.PROTECTED, PasswordPageState.CHANGED).contains("protection is active"))
        assertTrue(summary(ProtectionState.UNKNOWN, PasswordPageState.UNKNOWN).contains("protection state unknown"))
    }

    @Test
    fun anInterruptedRemovalSaysWhetherTheTagIsOpenNow() {
        fun summary(protection: ProtectionState) = SecuritySummary.of(
            SecurityOperation.REMOVE_PASSWORD,
            TagError.Security(SecurityFailure.Partial(SecurityOperation.REMOVE_PASSWORD, TagError.TagLost, 1, 4, 1, protection, PasswordPageState.UNCHANGED)),
        )

        assertTrue(summary(ProtectionState.NOT_PROTECTED).contains("protection removed, stored password not reset"))
        assertTrue(summary(ProtectionState.PROTECTED).contains("protection still active"))
        assertTrue(summary(ProtectionState.UNKNOWN).contains("protection state unknown"))
    }

    @Test
    fun aPartialLockWarnsThatItMayBePartiallyApplied() {
        val partial = TagError.Security(SecurityFailure.Partial(SecurityOperation.LOCK, TagError.TagLost, 2, 3))

        assertTrue(SecuritySummary.of(SecurityOperation.LOCK, partial).contains("may be partially applied"))
    }

    @Test
    fun otherFailuresNameTheReasonWithoutDetails() {
        assertEquals("Password protection set: failed (UID mismatch)", SecuritySummary.of(SecurityOperation.SET_PASSWORD, TagError.Security(SecurityFailure.UidMismatch)))
        assertEquals("Permanent lock applied: failed (read-only)", SecuritySummary.of(SecurityOperation.LOCK, TagError.ReadOnly))
        assertFalse(SecuritySummary.of(SecurityOperation.REMOVE_PASSWORD, TagError.AuthFailed(null)).contains("12345678"))
    }

    @Test
    fun securityEntriesMapToTheirOwnHistoryKindAndOutcomes() {
        assertEquals(HistoryKind.SECURITY, HistoryKind.fromCode("security"))
        val partial = TagError.Security(SecurityFailure.Partial(SecurityOperation.LOCK, TagError.TagLost, 1, 3))
        assertEquals(HistoryOutcome.FAILED_TAGLOST, HistoryOutcome.of(partial))
        assertEquals(HistoryOutcome.FAILED_AUTH, HistoryOutcome.of(TagError.Security(SecurityFailure.AuthExhausted)))
        assertEquals(HistoryOutcome.FAILED_OTHER, HistoryOutcome.of(TagError.Security(SecurityFailure.UidMismatch)))
    }

    @Test
    fun aPackMismatchIsAnAuthenticationFailureThatNamesTheCloneSuspicion() {
        assertEquals(HistoryOutcome.FAILED_AUTH, HistoryOutcome.of(TagError.Security(SecurityFailure.PackMismatch)))
        assertEquals(
            "Password protection set: failed (tag answered another PACK: possible clone)",
            SecuritySummary.of(SecurityOperation.SET_PASSWORD, TagError.Security(SecurityFailure.PackMismatch)),
        )
    }
}
