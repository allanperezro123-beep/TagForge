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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** NS-02: the exact command sequence, the protection it produces, and what happens when it is interrupted. */
class SetPasswordUseCaseTest {

    private val model = NtagModel.NTAG215
    private val password = Password.fromHex("12345678")!!
    private val request = PasswordRequest(password, pack = "AB CD".hex(), auth0 = 4)

    private fun plan(tag: FakeNtag21x, request: PasswordRequest, state: SecurityState) =
        SetPasswordUseCase.plan(state, request).value()

    private suspend fun FakeNtag21x.send(command: Bytes): Bytes = (transceive(command) as Outcome.Ok).value

    private suspend fun execute(tag: FakeNtag21x, plan: SecurityPlan) =
        SetPasswordUseCase(plan.confirmed()).runOn(sessionOf(tag))

    // ---- NS-02 "NTAG215 full protection exact commands" ------------------------------------------------------------

    @Test
    fun fullProtectionSendsTheFourWritesInTheSpecifiedOrder() = runTest {
        val tag = specTag()
        val plan = plan(tag, request, stateOf(tag))

        val result = execute(tag, plan)

        assertTrue(result is Outcome.Ok, "result: $result")
        assertEquals(
            listOf("A2 85 12 34 56 78", "A2 86 AB CD 00 00", "A2 84 80 00 00 00", "A2 83 04 00 00 04").map { it.hex() },
            tag.writeCommands,
            "PWD, PACK, CFG1 (ACCESS) and AUTH0 in CFG0 LAST",
        )
    }

    @Test
    fun theDryRunShowsTheSameFramesBeforeAnythingIsWritten() = runTest {
        val tag = specTag()
        val plan = plan(tag, request, stateOf(tag))

        assertEquals(
            listOf("A2 85 12 34 56 78", "A2 86 AB CD 00 00", "A2 84 80 00 00 00", "A2 83 04 00 00 04").map { it.hex() },
            plan.commands,
        )
        assertEquals(emptyList(), tag.writeCommands, "planning is read only")
        assertEquals(setOf(IrreversibleEffect.PASSWORD_PROTECTS_TAG), plan.effects)
    }

    @Test
    fun afterwardsTheTagNaksReadsUntilTheRightPasswordIsSent() = runTest {
        val tag = specTag()
        execute(tag, plan(tag, request, stateOf(tag)))
        tag.powerCycle()

        assertEquals(Bytes.of(0x00), tag.send(NtagCommands.read(4)), "READ page 04 is protected")
        assertEquals("AB CD".hex(), tag.send(NtagCommands.pwdAuth(password.bytes)))
        assertEquals(16, tag.send(NtagCommands.read(4)).size)
    }

    // ---- NS-02 "Write-only protection" -----------------------------------------------------------------------------

    @Test
    fun writeOnlyProtectionWritesAccessWithBitSevenClear() = runTest {
        val tag = specTag()
        val plan = plan(tag, request.copy(protectReads = false), stateOf(tag))

        execute(tag, plan)
        tag.powerCycle()

        assertEquals("A2 84 00 00 00 00".hex(), tag.writeCommands[2], "ACCESS bit 7 = 0")
        assertEquals(16, tag.send(NtagCommands.read(4)).size, "unauthenticated READ still works")
        assertEquals(Bytes.of(0x00), tag.send(NtagCommands.write(4, "01 02 03 04".hex())), "unauthenticated WRITE from AUTH0 NAKs")
        assertEquals(setOf(IrreversibleEffect.WRITE_PROTECTED_ONLY), plan.effects)
    }

    // ---- read-modify-write -----------------------------------------------------------------------------------------

    @Test
    fun unrelatedConfigurationBitsSurviveAndAuthLimitStaysWhereItWas() = runTest {
        val tag = specTag()
        tag.setPage(model.cfg0, "44 00 07 FF".hex())   // MIRROR bits and MIRROR_PAGE set by someone else
        tag.setPage(model.cfg1, "10 00 00 00".hex())   // NFC_CNT_EN on, AUTHLIM 0

        execute(tag, plan(tag, request, stateOf(tag)))

        assertEquals("A2 84 90 00 00 00".hex(), tag.writeCommands[2], "NFC_CNT_EN kept, PROT added, AUTHLIM still 0")
        assertEquals("A2 83 44 00 07 04".hex(), tag.writeCommands[3], "only byte 3 (AUTH0) of CFG0 changed")
    }

    @Test
    fun authLimitDefaultsToZeroAndTheRequestHasNoWayToChangeIt() = runTest {
        val tag = specTag()
        val written = plan(tag, request, stateOf(tag)).steps.filterIsInstance<PlanStep.WritePage>()

        assertEquals(0, written.first { it.page == model.cfg1 }.new[0] and 0x07)
    }

    // ---- password format -------------------------------------------------------------------------------------------

    @Test
    fun aPasswordIsExactlyFourBytes() {
        assertNotNull(Password.fromHex("12345678"))
        assertNotNull(Password.fromHex("12 34 56 78"))
        assertNull(Password.fromHex("123456"))
        assertNull(Password.fromHex("1234567890"))
        assertNull(Password.fromHex("1234567G"))
        assertNull(Password.fromHex(""))
        assertNull(Password.of("12 34 56".hex()))
    }

    @Test
    fun theFactoryValueIsNotAPasswordAndNothingPrintsTheSecret() {
        assertNull(Password.fromHex("FFFFFFFF"))
        assertEquals("Password(****)", password.toString())
    }

    @Test
    fun aGeneratedPasswordHasFourBytesAndIsReproducibleFromItsSeed() {
        val first = Password.random(kotlin.random.Random(7))
        assertEquals(4, first.bytes.size)
        assertEquals(first, Password.random(kotlin.random.Random(7)))
        assertEquals(2, Password.randomPack(kotlin.random.Random(7)).size)
    }

    @Test
    fun auth0MustProtectTheUserAreaAndThePasswordPage() = runTest {
        val tag = specTag()
        val state = stateOf(tag)

        assertEquals(SecurityFailure.Auth0OutOfRange, SetPasswordUseCase.plan(state, request.copy(auth0 = 3)).failure())
        assertEquals(SecurityFailure.Auth0OutOfRange, SetPasswordUseCase.plan(state, request.copy(auth0 = model.pwd + 1)).failure())
        assertTrue(SetPasswordUseCase.plan(state, request.copy(auth0 = model.pwd)) is Outcome.Ok)
    }

    // ---- refusals ----------------------------------------------------------------------------------------------------

    @Test
    fun configurationLockedForGoodRefusesBeforeAnyWrite() = runTest {
        val tag = specTag()
        tag.setPage(model.cfg1, "40 00 00 00".hex())
        tag.powerCycle()

        assertEquals(SecurityFailure.ConfigLocked, SetPasswordUseCase.plan(stateOf(tag), request).failure())
        assertEquals(emptyList(), tag.writeCommands)
    }

    @Test
    fun aDifferentTagIsNeverTouched() = runTest {
        val tag = specTag()
        val confirmed = plan(tag, request, stateOf(tag)).confirmed()
        val other = specTag(uid = "04 A1 B2 C3 D4 E5 F7".hex())

        val result = SetPasswordUseCase(confirmed).runOn(sessionOf(other))

        assertEquals(SecurityFailure.UidMismatch, result.securityFailure())
        assertEquals(emptyList(), other.commandLog, "not a single frame reached the other tag")
        assertEquals(emptyList(), tag.writeCommands)
    }

    @Test
    fun aTagChangedSincePlanningIsRefusedBeforeTheFirstWrite() = runTest {
        val tag = specTag()
        val plan = plan(tag, request, stateOf(tag))
        tag.setPage(model.cfg0, "84 00 00 FF".hex())

        val result = execute(tag, plan)

        assertEquals(SecurityFailure.StateChanged, result.securityFailure())
        assertEquals(emptyList(), tag.writeCommands)
    }

    // ---- NS-02 "TagLost mid-sequence" ------------------------------------------------------------------------------

    @Test
    fun tagLostAfterThePasswordButBeforeAuth0IsAPartialFailureWithProtectionOff() = runTest {
        val tag = specTag()
        val plan = plan(tag, request, stateOf(tag))
        tag.loseTagAfter(1)

        val failure = execute(tag, plan).securityFailure()

        assertEquals(SecurityFailure.Partial(SecurityOperation.SET_PASSWORD, TagError.TagLost, completedWrites = 1, totalWrites = 4), failure)
        assertEquals(0xFF, tag.page(model.cfg0)[3], "AUTH0 is written LAST, so protection is NOT active")
        assertEquals("12 34 56 78".hex(), tag.page(model.pwd))
    }

    @Test
    fun protectionIsNeverActiveAfterAFailureBeforeTheLastWrite() = runTest {
        for (lostAfter in 1..3) {
            val tag = specTag()
            val plan = plan(tag, request, stateOf(tag))
            tag.loseTagAfter(lostAfter)

            val result = execute(tag, plan)

            assertTrue(result is Outcome.Err, "lost after $lostAfter writes must fail")
            assertEquals(0xFF, tag.page(model.cfg0)[3], "lost after $lostAfter writes: AUTH0 untouched")
        }
    }

    @Test
    fun everyWriteDoneButNotVerifiedIsStillNotASuccess() = runTest {
        val tag = specTag()
        val plan = plan(tag, request, stateOf(tag))
        tag.loseTagAfter(4)

        val failure = execute(tag, plan).securityFailure() as SecurityFailure.Partial

        assertEquals(4, failure.completedWrites)
        assertEquals(4, failure.totalWrites)
        assertEquals(4, tag.page(model.cfg0)[3], "all writes landed; only the read-back was lost")
    }

    @Test
    fun tagLostBeforeTheFirstWriteChangesNothingAndIsAPlainTagLost() = runTest {
        val tag = specTag()
        val plan = plan(tag, request, stateOf(tag))
        tag.loseTagAfter(0)

        assertEquals(Outcome.Err(TagError.TagLost), execute(tag, plan))
        assertEquals(emptyList(), tag.writeCommands)
    }

    // ---- changing an existing password -----------------------------------------------------------------------------

    @Test
    fun anAlreadyProtectedTagIsAuthenticatedFirstAndThenReprogrammed() = runTest {
        val tag = specTag()
        tag.setPage(model.pwd, "12 34 56 78".hex())
        tag.setPage(model.pack, "AB CD 00 00".hex())
        tag.setPage(model.cfg1, "80 00 00 00".hex())
        tag.setPage(model.cfg0, "04 00 00 04".hex())
        val next = PasswordRequest(Password.fromHex("AABBCCDD")!!, "11 22".hex(), auth0 = 4, currentPassword = password)

        val plan = plan(tag, next, stateOf(tag, password))
        tag.powerCycle()
        val result = execute(tag, plan)
        tag.powerCycle()

        assertTrue(result is Outcome.Ok, "result: $result")
        assertEquals("1B 12 34 56 78".hex(), tag.commandLog.first { it[0] == NtagCommands.PWD_AUTH })
        assertEquals("11 22".hex(), tag.send(NtagCommands.pwdAuth("AA BB CC DD".hex())))
    }

    @Test
    fun aProtectedTagCannotBeReprogrammedWithoutItsCurrentPassword() = runTest {
        val tag = specTag()
        tag.setPage(model.cfg1, "80 00 00 00".hex())
        tag.setPage(model.cfg0, "04 00 00 04".hex())
        tag.setPage(model.pwd, "12 34 56 78".hex())
        val state = stateOf(tag, password)

        assertEquals(SecurityFailure.PasswordRequired, SetPasswordUseCase.plan(state, request).failure())
    }
}
