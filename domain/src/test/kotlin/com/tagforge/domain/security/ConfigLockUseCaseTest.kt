package com.tagforge.domain.security

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** NS-05: CFGLCK and AUTHLIM are a step of their own, with their own plan and their own typed confirmation. */
class ConfigLockUseCaseTest {

    private val model = NtagModel.NTAG215
    private val password = Password.fromHex("12345678")!!

    private suspend fun protectedTag(access: String = "80 00 00 00"): FakeNtag21x {
        val tag = specTag()
        val plan = SetPasswordUseCase.plan(stateOf(tag), PasswordRequest(password, "AB CD".hex(), auth0 = 4)).value()
        SetPasswordUseCase(plan.confirmed()).runOn(sessionOf(tag)).value()
        if (access != "80 00 00 00") tag.setPage(model.cfg1, access.hex())
        tag.powerCycle()
        return tag
    }

    private suspend fun lockPlan(tag: FakeNtag21x, request: ConfigLockRequest): Outcome<SecurityPlan, SecurityFailure> {
        val state = stateOf(tag, password)
        tag.powerCycle()
        return ConfigLockUseCase.plan(state, request)
    }

    @Test
    fun lockingTheConfigurationIsOneAccessWriteBehindAuthentication() = runTest {
        val plan = lockPlan(protectedTag(), ConfigLockRequest(lockConfig = true, currentPassword = password)).value()

        assertEquals(listOf("1B 12 34 56 78", "A2 84 C0 00 00 00").map { it.hex() }, plan.commands)
        assertEquals(setOf(IrreversibleEffect.CONFIG_PAGES_LOCKED_FOREVER), plan.effects)
        assertEquals(SecurityOperation.CONFIG_LOCK, plan.operation)
    }

    @Test
    fun anAttemptLimitAloneChangesOnlyTheThreeLowBitsOfAccess() = runTest {
        val plan = lockPlan(protectedTag(), ConfigLockRequest(authLimit = 3, currentPassword = password)).value()

        assertEquals("A2 84 83 00 00 00".hex(), plan.commands.last())
        assertEquals(setOf(IrreversibleEffect.AUTH_ATTEMPTS_LIMITED), plan.effects)
    }

    @Test
    fun bothTogetherAreStillASingleWriteWithBothEffects() = runTest {
        val plan = lockPlan(protectedTag(), ConfigLockRequest(lockConfig = true, authLimit = 3, currentPassword = password)).value()

        assertEquals("A2 84 C3 00 00 00".hex(), plan.commands.last())
        assertEquals(setOf(IrreversibleEffect.CONFIG_PAGES_LOCKED_FOREVER, IrreversibleEffect.AUTH_ATTEMPTS_LIMITED), plan.effects)
    }

    @Test
    fun unrelatedAccessBitsSurvive() = runTest {
        val tag = protectedTag(access = "90 00 00 00")   // PROT + NFC_CNT_EN

        val plan = lockPlan(tag, ConfigLockRequest(lockConfig = true, currentPassword = password)).value()

        assertEquals("A2 84 D0 00 00 00".hex(), plan.commands.last())
    }

    @Test
    fun executingLocksOnlyAfterAPowerCycleAndKeepsPwdAndPackWritable() = runTest {
        val tag = protectedTag()
        val plan = lockPlan(tag, ConfigLockRequest(lockConfig = true, currentPassword = password)).value()

        ConfigLockUseCase(plan.confirmed()).runOn(sessionOf(tag)).value()
        tag.powerCycle()
        tag.transceive(NtagCommands.pwdAuth(password.bytes))

        assertEquals("C0 00 00 00".hex(), tag.page(model.cfg1))
        assertEquals(Outcome.Ok("00".hex()), tag.transceive(NtagCommands.write(model.cfg0, "04 00 00 FF".hex())), "CFG0 is write locked now")
        assertEquals(Outcome.Ok("0A".hex()), tag.transceive(NtagCommands.write(model.pwd, "01 02 03 04".hex())), "PWD stays writable")
    }

    @Test
    fun afterTheLimitIsSetWrongPasswordsLockTheAreaForGood() = runTest {
        val tag = protectedTag()
        val plan = lockPlan(tag, ConfigLockRequest(authLimit = 2, currentPassword = password)).value()
        ConfigLockUseCase(plan.confirmed()).runOn(sessionOf(tag)).value()
        tag.powerCycle()

        repeat(2) { AuthenticateUseCase(Password.fromHex("00000001")!!).runOn(sessionOf(tag)) }

        assertEquals(SecurityFailure.AuthExhausted, AuthenticateUseCase(password).runOn(sessionOf(tag)).securityFailure())
    }

    @Test
    fun nothingRequestedOrAlreadyLockedIsRefusedWithoutWriting() = runTest {
        val tag = protectedTag()
        assertEquals(SecurityFailure.NothingToChange, lockPlan(tag, ConfigLockRequest(currentPassword = password)).failure())

        tag.setPage(model.cfg1, "C0 00 00 00".hex())
        assertEquals(SecurityFailure.ConfigLocked, lockPlan(tag, ConfigLockRequest(lockConfig = true, currentPassword = password)).failure())
    }

    @Test
    fun theAttemptLimitIsThreeBits() {
        assertFailsWith<IllegalArgumentException> { ConfigLockRequest(authLimit = 8) }
        assertFailsWith<IllegalArgumentException> { ConfigLockRequest(authLimit = -1) }
    }

    @Test
    fun aProtectedTagNeedsTheCurrentPasswordInTheRequest() = runTest {
        val tag = protectedTag()
        val state = stateOf(tag, password)

        assertEquals(SecurityFailure.PasswordRequired, ConfigLockUseCase.plan(state, ConfigLockRequest(lockConfig = true)).failure())
    }

    @Test
    fun aPlanForAnotherOperationCannotBeRunAsAConfigLock() = runTest {
        val tag = specTag()
        val passwordPlan = SetPasswordUseCase.plan(stateOf(tag), PasswordRequest(password, "AB CD".hex(), auth0 = 4)).value()

        val failure = ConfigLockUseCase(passwordPlan.confirmed()).runOn(sessionOf(tag)).securityFailure()

        assertEquals(SecurityFailure.WrongPlan, failure)
        assertEquals(emptyList(), tag.writeCommands)
    }

    @Test
    fun aDifferentTagIsNeverTouchedAndTagLostIsAPartialFailure() = runTest {
        val tag = protectedTag()
        val confirmed = lockPlan(tag, ConfigLockRequest(lockConfig = true, currentPassword = password)).value().confirmed()
        val other = specTag(uid = "04 A1 B2 C3 D4 E5 F7".hex())
        assertEquals(SecurityFailure.UidMismatch, ConfigLockUseCase(confirmed).runOn(sessionOf(other)).securityFailure())
        assertTrue(other.commandLog.isEmpty())

        tag.failNextAfter(2, TagError.TagLost)   // PWD_AUTH and the preflight read pass, the write is lost
        val failure = ConfigLockUseCase(confirmed).runOn(sessionOf(tag)).securityFailure()
        assertEquals(SecurityFailure.Partial(SecurityOperation.CONFIG_LOCK, TagError.TagLost, completedWrites = 0, totalWrites = 1), failure)
    }
}
