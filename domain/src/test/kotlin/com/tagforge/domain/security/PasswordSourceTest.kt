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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WU4: with a master passphrase the password is derived for the UID of the tag that answers at tap time, so one typed
 * passphrase unlocks a whole fleet, one PWD_AUTH per tap, and the derived PACK doubles as the clone check.
 */
class PasswordSourceTest {

    private val model = NtagModel.NTAG215
    private val phrase = "correct horse battery staple"
    private val otherUid = "04 11 22 33 44 55 66".hex()

    private fun diversifiedTag(forUid: Bytes, access: String = "80 00 00 00", tagUid: Bytes = forUid): FakeNtag21x {
        val (password, pack) = DiversifiedPassword.derive(phrase, forUid)
        return specTag(model, tagUid).also {
            it.setPage(model.pwd, password.bytes)
            it.setPage(model.pack, pack + Bytes.of(0, 0))
            it.setPage(model.cfg1, access.hex())
            it.setPage(model.cfg0, "04 00 00 04".hex())
        }
    }

    private fun FakeNtag21x.authFrames() = commandLog.filter { it[0] == NtagCommands.PWD_AUTH }

    @Test
    fun aTypedSourceIsTheSamePasswordForEveryTagAndKnowsNoPack() {
        val typed = PasswordSource.Typed(Password.fromHex("12345678")!!)

        assertEquals(typed.forTag(SPEC_UID), typed.forTag(otherUid))
        assertEquals(null, typed.forTag(SPEC_UID).pack)
    }

    @Test
    fun aDiversifiedSourceGivesEachUidItsOwnPasswordAndPack() {
        val source = PasswordSource.Diversified(phrase)

        assertEquals(ResolvedPassword(DiversifiedPassword.derive(phrase, SPEC_UID).first, DiversifiedPassword.derive(phrase, SPEC_UID).second), source.forTag(SPEC_UID))
        assertTrue(source.forTag(SPEC_UID).password != source.forTag(otherUid).password)
    }

    @Test
    fun theSourceNeverPrintsThePassphrase() {
        assertFalse(PasswordSource.Diversified(phrase).toString().contains("horse"))
        assertFalse(PasswordSource.Typed(Password.fromHex("12345678")!!).toString().contains("12345678"))
    }

    @Test
    fun unlockingWithThePassphraseDerivesForTheTappedTagAndSendsOnePwdAuth() = runTest {
        val tag = diversifiedTag(SPEC_UID)
        val (password, pack) = DiversifiedPassword.derive(phrase, SPEC_UID)

        val report = AuthenticateUseCase(PasswordSource.Diversified(phrase)).runOn(sessionOf(tag)).value()

        assertEquals(pack, report.pack)
        assertEquals(true, report.packMatches, "the derived PACK is the one this tag must answer")
        assertEquals(listOf(Bytes.of(NtagCommands.PWD_AUTH) + password.bytes), tag.authFrames())
    }

    @Test
    fun aTagDiversifiedForAnotherUidIsRejectedWithASingleAttempt() = runTest {
        val tag = diversifiedTag(forUid = otherUid, tagUid = SPEC_UID, access = "83 00 00 00")

        val result = AuthenticateUseCase(PasswordSource.Diversified(phrase)).runOn(sessionOf(tag))

        assertEquals(Outcome.Err(TagError.AuthFailed(null)), result)
        assertEquals(1, tag.authFrames().size, "no retry, no second guess")
        assertEquals(1, tag.authFailures)
    }

    @Test
    fun anExplicitExpectedPackStillWinsOverTheDerivedOne() = runTest {
        val tag = diversifiedTag(SPEC_UID)

        val report = AuthenticateUseCase(PasswordSource.Diversified(phrase), expectedPack = "11 22".hex()).runOn(sessionOf(tag)).value()

        assertEquals(false, report.packMatches)
    }
}
