package com.tagforge.domain.security

import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ntag.Region
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** NS-01: dry-run, typed confirmation (last 4 hex of the UID, case-insensitive) and the UID re-check. */
class SecurityPlanTest {

    private val uid = "04 A1 B2 C3 D4 E5 F6".hex()
    private val model = NtagModel.NTAG215

    private val plan = SecurityPlan(
        operation = SecurityOperation.SET_PASSWORD,
        uid = uid,
        model = model,
        steps = listOf(
            PlanStep.WritePage(model.pwd, old = null, new = "12 34 56 78".hex()),
            PlanStep.WritePage(model.cfg0, old = "04 00 00 FF".hex(), new = "04 00 00 04".hex()),
        ),
        effects = setOf(IrreversibleEffect.PASSWORD_PROTECTS_TAG),
    )

    // ---- typed confirmation ------------------------------------------------------------------------

    @Test
    fun theExpectedTextIsTheLastFourHexCharactersOfTheUid() {
        assertEquals("E5F6", UidConfirmation.expected(uid))
        assertEquals("0102", UidConfirmation.expected("AA BB CC DD 01 02".hex()))
    }

    @Test
    fun theLastFourHexCharactersMatchCaseInsensitively() {
        assertTrue(UidConfirmation.matches(uid, "E5F6"))
        assertTrue(UidConfirmation.matches(uid, "e5f6"))
        assertTrue(UidConfirmation.matches(uid, "  e5F6 "))
    }

    @Test
    fun anythingElseKeepsConfirmDisabled() {
        assertFalse(UidConfirmation.matches(uid, "E5F7"))
        assertFalse(UidConfirmation.matches(uid, ""))
        assertFalse(UidConfirmation.matches(uid, "E5F"))
        assertFalse(UidConfirmation.matches(uid, "5F6"))
        assertFalse(UidConfirmation.matches(uid, "D4E5F6"))
        assertFalse(UidConfirmation.matches(uid, "LOCK"))
    }

    @Test
    fun onlyTheRightTextYieldsAConfirmedPlan() {
        val confirmed = plan.confirm("e5f6")
        assertNotNull(confirmed)
        assertSame(plan, confirmed.plan)
        assertNull(plan.confirm("E5F7"))
        assertNull(plan.confirm(""))
    }

    // ---- the same tag at execution time --------------------------------------------------------------

    @Test
    fun theTappedUidMustEqualTheConfirmedOne() {
        assertTrue(plan.isForTag(uid))
        assertFalse(plan.isForTag("04 A1 B2 C3 D4 E5 F7".hex()))
        assertFalse(plan.isForTag("A1 B2 C3 D4 E5 F6".hex()))
    }

    // ---- dry-run -------------------------------------------------------------------------------------

    @Test
    fun theDryRunListsEveryExactCommandInOrder() {
        assertEquals(
            listOf("A2 85 12 34 56 78".hex(), "A2 83 04 00 00 04".hex()),
            plan.commands,
        )
        assertEquals(listOf("A2 85 12 34 56 78", "A2 83 04 00 00 04"), plan.dryRun().map { it.commandHex })
    }

    @Test
    fun theDryRunShowsOldAndNewPageContentAndTheRegion() {
        val lines = plan.dryRun()

        assertEquals(listOf(model.pwd, model.cfg0), lines.map { it.page })
        assertEquals(listOf(Region.PWD, Region.CFG0), lines.map { it.region })
        assertEquals("04 00 00 FF", lines[1].oldHex)
        assertEquals("04 00 00 04", lines[1].newHex)
    }

    @Test
    fun theOldValueOfAPageTheChipNeverRevealsIsNotShown() {
        val line = plan.dryRun().first()

        assertNull(line.oldHex, "PWD always reads as 00, so no old value is claimed")
        assertTrue(line.oldHidden)
        assertFalse(plan.dryRun()[1].oldHidden)
    }

    @Test
    fun aPlatformStepHasNoCommandButStaysInTheStepList() {
        val lock = plan.copy(
            operation = SecurityOperation.LOCK,
            steps = listOf(PlanStep.MakeReadOnly),
            effects = setOf(IrreversibleEffect.LOCK_BITS_NEVER_CLEARED),
        )

        assertTrue(lock.commands.isEmpty())
        assertEquals(1, lock.steps.size)
        assertTrue(lock.dryRun().isEmpty())
    }
}
