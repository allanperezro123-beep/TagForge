package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** NS-04: authenticate first, switch protection off FIRST, then clear PROT, PWD and PACK. */
class RemovePasswordUseCaseTest {

    private val model = NtagModel.NTAG215
    private val password = Password.fromHex("12345678")!!

    /** A tag protected exactly as in NS-02, through the real set-password flow. */
    private suspend fun protectedTag(): FakeNtag21x {
        val tag = specTag()
        val request = PasswordRequest(password, "AB CD".hex(), auth0 = 4)
        val plan = SetPasswordUseCase.plan(stateOf(tag), request).value()
        SetPasswordUseCase(plan.confirmed()).runOn(sessionOf(tag)).value()
        tag.powerCycle()
        return tag
    }

    private suspend fun removalPlan(tag: FakeNtag21x): SecurityPlan {
        val state = stateOf(tag, password)
        tag.powerCycle()
        return RemovePasswordUseCase.plan(state, password).value()
    }

    private suspend fun FakeNtag21x.send(command: Bytes): Bytes = (transceive(command) as Outcome.Ok).value

    @Test
    fun theRemovalSequenceStartsWithAuthenticationThenAuth0IsSwitchedOffFirst() = runTest {
        val tag = protectedTag()
        val plan = removalPlan(tag)

        assertEquals(
            listOf("1B 12 34 56 78", "A2 83 04 00 00 FF", "A2 84 00 00 00 00", "A2 85 FF FF FF FF", "A2 86 00 00 00 00").map { it.hex() },
            plan.commands,
        )
        val before = tag.writeCommands.size
        RemovePasswordUseCase(plan.confirmed()).runOn(sessionOf(tag)).value()
        assertEquals(plan.commands.drop(1), tag.writeCommands.drop(before))
    }

    @Test
    fun afterwardsAnUnauthenticatedReadWorksAndThePasswordIsTheFactoryOne() = runTest {
        val tag = protectedTag()
        RemovePasswordUseCase(removalPlan(tag).confirmed()).runOn(sessionOf(tag)).value()
        tag.powerCycle()

        assertEquals(16, tag.send(NtagCommands.read(4)).size, "READ page 04 no longer needs a password")
        assertEquals("00 00 00 00".hex(), tag.send(NtagCommands.read(model.pwd)).slice(0, 4))
        assertEquals("00 00".hex(), tag.send(NtagCommands.pwdAuth("FF FF FF FF".hex())), "PWD = FFFFFFFF and PACK = 0000 again")
        assertEquals(0xFF, tag.page(model.cfg0)[3])
        assertEquals(0, tag.page(model.cfg1)[0] and 0x80)
    }

    @Test
    fun theOriginalPasswordIsNeededToPlanTheRemoval() = runTest {
        val tag = protectedTag()

        val without = ReadSecurityStateUseCase().runOn(sessionOf(tag))
        val wrong = ReadSecurityStateUseCase(Password.fromHex("00000001")).runOn(sessionOf(tag))

        assertEquals(Outcome.Err(TagError.AuthRequired), without)
        assertEquals(Outcome.Err(TagError.AuthFailed(null)), wrong)
    }

    @Test
    fun aTagWithoutProtectionHasNothingToRemove() = runTest {
        val state = stateOf(specTag())

        assertEquals(SecurityFailure.NotProtected, RemovePasswordUseCase.plan(state, password).failure())
    }

    @Test
    fun lockedConfigurationWithAuth0SetCannotBeUnprotectedAnymore() = runTest {
        val tag = protectedTag()
        tag.setPage(model.cfg1, "C0 00 00 00".hex())   // PROT + CFGLCK
        tag.powerCycle()
        val state = stateOf(tag, password)

        assertEquals(SecurityFailure.Unremovable, RemovePasswordUseCase.plan(state, password).failure())
        assertEquals(1, tag.writeCommands.count { it[1] == model.cfg0 }, "only the original protection write ever touched CFG0")
    }

    @Test
    fun anInterruptedRemovalNeverLeavesProtectionHalfConfigured() = runTest {
        for (lostAfter in 1..3) {
            val tag = protectedTag()
            val plan = removalPlan(tag)
            tag.loseTagAfter(tag.writeCommands.size + lostAfter)

            val result = RemovePasswordUseCase(plan.confirmed()).runOn(sessionOf(tag))

            val failure = result.securityFailure() as SecurityFailure.Partial
            assertEquals(SecurityOperation.REMOVE_PASSWORD, failure.operation)
            assertEquals(lostAfter, failure.completedWrites)
            assertEquals(0xFF, tag.page(model.cfg0)[3], "AUTH0 went first: the tag is open, not half protected")
        }
    }

    @Test
    fun aDifferentTagIsNeverTouched() = runTest {
        val tag = protectedTag()
        val confirmed = removalPlan(tag).confirmed()
        val other = specTag(uid = "04 A1 B2 C3 D4 E5 F7".hex())

        val failure = RemovePasswordUseCase(confirmed).runOn(sessionOf(other)).securityFailure()

        assertEquals(SecurityFailure.UidMismatch, failure)
        assertTrue(other.commandLog.isEmpty())
    }
}
