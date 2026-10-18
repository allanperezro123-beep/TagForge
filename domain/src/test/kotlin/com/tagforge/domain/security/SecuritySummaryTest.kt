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
        val partial = TagError.Security(SecurityFailure.Partial(SecurityOperation.SET_PASSWORD, TagError.TagLost, 1, 4))

        val summary = SecuritySummary.of(SecurityOperation.SET_PASSWORD, partial)

        assertTrue(summary.contains("interrupted after 1 of 4 writes"), summary)
        assertTrue(summary.contains("protection may NOT be active"), summary)
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
}
