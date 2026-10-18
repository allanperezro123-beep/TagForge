package com.tagforge.app.infrastructure.persistence

import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryQuery
import com.tagforge.domain.history.OutcomeFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Translation of a [HistoryQuery] into the arguments of the DAO's single parameterized SELECT. */
class HistoryQueryArgsTest {

    @Test
    fun anUnfilteredQueryOpensEveryDoorAndNeverPassesAnEmptyInList() {
        val args = HistoryQuery().toArgs()
        assertEquals("", args.text)
        assertEquals("", args.uid)
        assertTrue(args.allKinds)
        assertEquals(listOf(""), args.kinds)
        assertEquals(HistoryQueryArgs.OUTCOME_ANY, args.outcome)
        assertEquals(Long.MIN_VALUE, args.from)
        assertEquals(Long.MAX_VALUE, args.to)
        assertEquals(HistoryQuery.DEFAULT_LIMIT, args.limit)
    }

    @Test
    fun aFilteredQueryCarriesCodesBoundsAndTheUidNeedle() {
        val args = HistoryQuery(
            limit = 20,
            text = " 04:a1 ",
            kinds = setOf(HistoryKind.WRITE, HistoryKind.ERASE),
            outcome = OutcomeFilter.FAILED,
            from = Instant.fromEpochMilliseconds(1_000),
            to = Instant.fromEpochMilliseconds(2_000),
        ).toArgs()

        assertEquals("04:a1", args.text)
        assertEquals("04A1", args.uid)
        assertFalse(args.allKinds)
        assertEquals(setOf("write", "erase"), args.kinds.toSet())
        assertEquals(HistoryQueryArgs.OUTCOME_FAILED, args.outcome)
        assertEquals(1_000L, args.from)
        assertEquals(2_000L, args.to)
        assertEquals(20, args.limit)
    }

    @Test
    fun successMapsToItsOwnOutcomeCode() {
        assertEquals(HistoryQueryArgs.OUTCOME_SUCCESS, HistoryQuery(outcome = OutcomeFilter.SUCCESS).toArgs().outcome)
    }

    @Test
    fun likeWildcardsTypedByTheUserAreEscapedSoTheyMatchLiterally() {
        assertEquals("100\\% a\\_b \\\\", HistoryQuery(text = "100% a_b \\").toArgs().text)
        assertEquals("plain", HistoryQuery(text = "plain").toArgs().text)
    }
}
