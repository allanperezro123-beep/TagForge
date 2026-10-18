package com.tagforge.domain.security

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** NS-03: authenticate once per tap, warn about clones, expose AUTHLIM, never retry and never guess. */
class AuthenticateUseCaseTest {

    private val model = NtagModel.NTAG215
    private val right = Password.fromHex("12345678")!!
    private val wrong = Password.fromHex("00000001")!!

    private fun protectedTag(access: String = "80 00 00 00"): FakeNtag21x = specTag().also {
        it.setPage(model.pwd, "12 34 56 78".hex())
        it.setPage(model.pack, "AB CD 00 00".hex())
        it.setPage(model.cfg1, access.hex())
        it.setPage(model.cfg0, "04 00 00 04".hex())
    }

    private fun FakeNtag21x.authFrames() = commandLog.filter { it[0] == NtagCommands.PWD_AUTH }

    @Test
    fun theRightPasswordUnlocksAndReturnsThePack() = runTest {
        val tag = protectedTag()

        val report = AuthenticateUseCase(right).runOn(sessionOf(tag)).value()

        assertEquals("AB CD".hex(), report.pack)
        assertNull(report.packMatches, "no expected PACK was given")
        assertEquals(listOf("1B 12 34 56 78".hex()), tag.authFrames())
    }

    @Test
    fun aWrongPasswordIsOneAttemptAndNeverRetried() = runTest {
        val tag = protectedTag()

        val result = AuthenticateUseCase(wrong).runOn(sessionOf(tag))

        assertEquals(Outcome.Err(TagError.AuthFailed(null)), result)
        assertEquals(1, tag.authFrames().size, "exactly one PWD_AUTH frame: no automatic retry")
    }

    @Test
    fun aPackThatDiffersFromTheExpectedOneWarnsThatTheTagMayBeAClone() = runTest {
        val mismatch = AuthenticateUseCase(right, expectedPack = "11 22".hex()).runOn(sessionOf(protectedTag())).value()
        val match = AuthenticateUseCase(right, expectedPack = "AB CD".hex()).runOn(sessionOf(protectedTag())).value()

        assertEquals(false, mismatch.packMatches)
        assertTrue(mismatch.possibleClone)
        assertEquals(true, match.packMatches)
        assertEquals(false, match.possibleClone)
    }

    @Test
    fun theAttemptLimitIsReadableBeforeTheFirstTryWhenTheConfigIsOpen() = runTest {
        val tag = protectedTag(access = "03 00 00 00")   // PROT = 0, AUTHLIM = 3: CFG1 stays readable

        val info = ReadAuthLimitUseCase().runOn(sessionOf(tag)).value()

        assertEquals(3, info.authLimit)
        assertEquals(emptyList(), tag.authFrames(), "reading the limit never spends an attempt")
    }

    @Test
    fun theAttemptLimitIsUnknownWhenTheConfigIsBehindThePassword() = runTest {
        val info = ReadAuthLimitUseCase().runOn(sessionOf(protectedTag(access = "83 00 00 00"))).value()

        assertNull(info.authLimit)
    }

    @Test
    fun anUnlimitedTagReportsZero() = runTest {
        val info = ReadAuthLimitUseCase().runOn(sessionOf(protectedTag(access = "00 00 00 00"))).value()

        assertEquals(0, info.authLimit)
    }

    @Test
    fun onceTheLimitIsExhaustedEvenTheRightPasswordIsRejectedAndExplained() = runTest {
        val tag = protectedTag(access = "83 00 00 00")
        repeat(3) { AuthenticateUseCase(wrong).runOn(sessionOf(tag)) }

        val failure = AuthenticateUseCase(right).runOn(sessionOf(tag)).securityFailure()

        assertEquals(SecurityFailure.AuthExhausted, failure)
    }

    @Test
    fun tagLostDuringAuthenticationIsTagLost() = runTest {
        val tag = protectedTag()
        tag.failNext(TagError.TagLost)

        assertEquals(Outcome.Err(TagError.TagLost), AuthenticateUseCase(right).runOn(sessionOf(tag)))
    }

    @Test
    fun aTagThatIsNotAnNtagIsUnsupported() = runTest {
        val failure = AuthenticateUseCase(right).runOn(FakeTagSession.withoutNtag()).securityFailure()

        assertEquals(SecurityFailure.UnsupportedChip, failure)
    }
}
