package com.tagforge.domain.security

import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An interrupted set/remove password says, as typed data, which write failed and what the tag looks like now: is the protection
 * active, and was the password page already rewritten. The report follows the PLAN (order of the writes), never a guess.
 */
class PartialFailureReportTest {

    private val model = NtagModel.NTAG215
    private val password = Password.fromHex("12345678")!!
    private val request = PasswordRequest(password, pack = "AB CD".hex(), auth0 = 4)

    // set-password writes: 0 PWD, 1 PACK, 2 CFG1, 3 CFG0 (AUTH0 last). Execution commands before the first write: 2 pre-flight reads.
    private suspend fun planFor(tag: FakeNtag21x): SecurityPlan = SetPasswordUseCase.plan(stateOf(tag), request).value()

    /** Fault injection counts commands from the moment it is armed, so the plan is built (it reads the tag) BEFORE arming. */
    private suspend fun setPassword(tag: FakeNtag21x, plan: SecurityPlan): SecurityFailure.Partial =
        SetPasswordUseCase(plan.confirmed()).runOn(sessionOf(tag)).securityFailure() as SecurityFailure.Partial

    @Test
    fun lostAfterThePasswordPageWasWrittenSaysNotProtectedButThePasswordPageChanged() = runTest {
        val tag = specTag()
        val plan = planFor(tag)
        tag.loseTagAfter(1)   // PWD landed, PACK in flight

        val partial = setPassword(tag, plan)

        assertEquals(1, partial.failedStep)
        assertEquals(ProtectionState.NOT_PROTECTED, partial.protection)
        assertEquals(PasswordPageState.CHANGED, partial.passwordPage)
        assertEquals(1, partial.completedWrites)
        assertEquals(4, partial.totalWrites)
    }

    @Test
    fun aLinkDropOnThePasswordWriteItselfLeavesThePasswordPageUnknown() = runTest {
        val tag = specTag()
        val plan = planFor(tag)
        tag.failNextAfter(2, TagError.TagLost)   // the third command: the PWD write, which may or may not have landed

        val partial = setPassword(tag, plan)

        assertEquals(0, partial.failedStep)
        assertEquals(ProtectionState.NOT_PROTECTED, partial.protection)
        assertEquals(PasswordPageState.UNKNOWN, partial.passwordPage)
    }

    @Test
    fun aRefusalOnTheLastWriteLeavesTheTagNotProtectedWithTheNewPassword() = runTest {
        val tag = specTag()
        val plan = planFor(tag)
        tag.failNextAfter(5, TagError.Nak(0))   // AUTH0 write refused: it did not land

        val partial = setPassword(tag, plan)

        assertEquals(3, partial.failedStep)
        assertEquals(ProtectionState.NOT_PROTECTED, partial.protection)
        assertEquals(PasswordPageState.CHANGED, partial.passwordPage)
    }

    @Test
    fun aLinkDropOnTheAuth0WriteMeansProtectionMayBeActive() = runTest {
        val tag = specTag()
        val plan = planFor(tag)
        tag.failNextAfter(5, TagError.TagLost)   // AUTH0 write in flight: it may have landed

        val partial = setPassword(tag, plan)

        assertEquals(3, partial.failedStep)
        assertEquals(ProtectionState.UNKNOWN, partial.protection)
        assertEquals(PasswordPageState.CHANGED, partial.passwordPage)
    }

    @Test
    fun everyWriteDoneButTheReadBackLostMeansProtected() = runTest {
        val tag = specTag()
        val plan = planFor(tag)
        tag.loseTagAfter(4)

        val partial = setPassword(tag, plan)

        assertEquals(null, partial.failedStep, "no write failed: the check afterwards did")
        assertEquals(ProtectionState.PROTECTED, partial.protection)
        assertEquals(PasswordPageState.CHANGED, partial.passwordPage)
        assertEquals(4, partial.completedWrites)
    }

    @Test
    fun reprogrammingAProtectedTagKeepsItProtectedEvenIfInterrupted() = runTest {
        val tag = specTag()
        tag.setPage(model.pwd, "12 34 56 78".hex())
        tag.setPage(model.pack, "AB CD 00 00".hex())
        tag.setPage(model.cfg1, "80 00 00 00".hex())
        tag.setPage(model.cfg0, "04 00 00 04".hex())
        val next = request.copy(password = Password.fromHex("AABBCCDD")!!, currentPassword = password)
        val plan = SetPasswordUseCase.plan(stateOf(tag, password), next).value()
        tag.powerCycle()
        tag.loseTagAfter(1)

        val partial = setPassword(tag, plan)

        assertEquals(ProtectionState.PROTECTED, partial.protection)
        assertEquals(PasswordPageState.CHANGED, partial.passwordPage)
    }

    // ---- remove password: writes 0 CFG0 (AUTH0 = FF first), 1 CFG1, 2 PWD, 3 PACK; a leading authentication and 2 pre-flight reads -----

    private suspend fun protectedTag(): FakeNtag21x {
        val tag = specTag()
        SetPasswordUseCase(SetPasswordUseCase.plan(stateOf(tag), request).value().confirmed()).runOn(sessionOf(tag)).value()
        tag.powerCycle()
        return tag
    }

    private suspend fun remove(tag: FakeNtag21x): SecurityFailure.Partial {
        val state = stateOf(tag, password)
        tag.powerCycle()
        val plan = RemovePasswordUseCase.plan(state, password).value()
        return RemovePasswordUseCase(plan.confirmed()).runOn(sessionOf(tag)).securityFailure() as SecurityFailure.Partial
    }

    @Test
    fun removalInterruptedAfterAuth0WasClearedSaysNotProtectedAndThePasswordPageUntouched() = runTest {
        val tag = protectedTag()
        tag.loseTagAfter(tag.writeCount + 1)   // CFG0 landed, CFG1 in flight

        val partial = remove(tag)

        assertEquals(1, partial.failedStep)
        assertEquals(ProtectionState.NOT_PROTECTED, partial.protection)
        assertEquals(PasswordPageState.UNCHANGED, partial.passwordPage)
        assertEquals(0xFF, tag.page(model.cfg0)[3])
    }

    @Test
    fun removalInterruptedOnTheAuth0WriteMeansProtectionMayBeGone() = runTest {
        val tag = protectedTag()
        val state = stateOf(tag, password)
        tag.powerCycle()
        val plan = RemovePasswordUseCase.plan(state, password).value()
        tag.failNextAfter(3, TagError.TagLost)   // auth, 2 pre-flight reads, then the CFG0 write

        val partial = RemovePasswordUseCase(plan.confirmed()).runOn(sessionOf(tag)).securityFailure() as SecurityFailure.Partial

        assertEquals(0, partial.failedStep)
        assertEquals(ProtectionState.UNKNOWN, partial.protection)
        assertEquals(PasswordPageState.UNCHANGED, partial.passwordPage)
    }

    @Test
    fun removalInterruptedOnTheLastWriteLeavesTheTagOpenWithThePasswordPageRewritten() = runTest {
        val tag = protectedTag()
        tag.loseTagAfter(tag.writeCount + 3)   // CFG0, CFG1 and PWD landed, PACK in flight

        val partial = remove(tag)

        assertEquals(3, partial.failedStep)
        assertEquals(ProtectionState.NOT_PROTECTED, partial.protection)
        assertEquals(PasswordPageState.CHANGED, partial.passwordPage)
    }
}
