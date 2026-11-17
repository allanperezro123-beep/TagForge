package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FixedClock
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** WU3: `ensureUnlocked` sends AT MOST ONE PWD_AUTH with the remembered password and never guesses or retries. */
class TagUnlockTest {

    private val model = NtagModel.NTAG215
    private val right = Password.fromHex("12345678")!!
    private val wrong = Password.fromHex("00000001")!!
    private val store = SessionCredentials(FixedClock())

    private fun protectedTag(authLimit: Int = 0): FakeNtag21x = specTag().also {
        it.setPage(model.pwd, "12 34 56 78".hex())
        it.setPage(model.pack, "AB CD 00 00".hex())
        it.setPage(model.cfg1, Bytes.of(0x80 or authLimit, 0, 0, 0))
        it.setPage(model.cfg0, "04 00 00 04".hex())
    }

    private fun FakeNtag21x.authFrames() = commandLog.filter { it[0] == NtagCommands.PWD_AUTH }

    @Test
    fun theRememberedPasswordIsSentOnceAndUnlocksTheTag() = runTest {
        val tag = protectedTag()
        store.remember(SPEC_UID, right)

        val result = TagUnlock.ensureUnlocked(tag, SPEC_UID, store)

        assertEquals(Outcome.Ok(Unit), result)
        assertTrue(tag.authenticated)
        assertEquals(listOf("1B 12 34 56 78".hex()), tag.authFrames())
    }

    @Test
    fun withoutACredentialNothingIsSentAtAll() = runTest {
        val tag = protectedTag()
        store.remember("04 11 22 33 44 55 66".hex(), right)   // another tag's password is not ours to send

        val result = TagUnlock.ensureUnlocked(tag, SPEC_UID, store)

        assertEquals(Outcome.Ok(Unit), result)
        assertEquals(0, tag.authFrames().size, "zero PWD_AUTH frames")
        assertEquals(0, tag.transceiveCount, "the tag was not even addressed")
    }

    @Test
    fun aWrongRememberedPasswordFailsOnceIsForgottenAndNeverSentAgain() = runTest {
        val tag = protectedTag()
        store.remember(SPEC_UID, wrong)

        val first = TagUnlock.ensureUnlocked(tag, SPEC_UID, store)
        val second = TagUnlock.ensureUnlocked(tag, SPEC_UID, store)

        assertEquals(Outcome.Err(TagError.AuthFailed(null)), first)
        assertEquals(Outcome.Ok(Unit), second, "with nothing remembered the second operation just proceeds")
        assertEquals(1, tag.authFrames().size, "the wrong password reached the tag exactly once")
        assertNull(store.credentialFor(SPEC_UID))
    }

    @Test
    fun anExhaustedAttemptLimitIsReportedAndTheCredentialForgotten() = runTest {
        val tag = protectedTag(authLimit = 1)
        store.remember(SPEC_UID, wrong)
        TagUnlock.ensureUnlocked(tag, SPEC_UID, store)            // the one allowed failure is spent
        store.remember(SPEC_UID, right)

        val result = TagUnlock.ensureUnlocked(tag, SPEC_UID, store)

        assertEquals(Outcome.Err(TagError.Security(SecurityFailure.AuthExhausted)), result)
        assertNull(store.credentialFor(SPEC_UID))
    }

    @Test
    fun aLinkThatDropsRightAfterTheAuthIsUnknownNotLostAndTheCredentialIsForgotten() = runTest {
        for (error in listOf(TagError.TagLost, TagError.Timeout, TagError.Io("x"))) {
            val tag = protectedTag().also { it.dropLinkOnRefusedAuth = error }
            store.remember(SPEC_UID, wrong)

            val result = TagUnlock.ensureUnlocked(tag, SPEC_UID, store)

            assertEquals(Outcome.Err(TagError.Security(SecurityFailure.AuthOutcomeUnknown)), result, "$error")
            assertNull(store.credentialFor(SPEC_UID), "$error")
            assertEquals(1, tag.authFrames().size, "$error: sent once, never retried")
        }
    }

    @Test
    fun aPackThatDiffersFromTheExpectedOneStopsTheOperationAndForgetsThePassword() = runTest {
        val tag = protectedTag()
        store.remember(SPEC_UID, right, pack = "11 22".hex())

        val result = TagUnlock.ensureUnlocked(tag, SPEC_UID, store)

        assertEquals(Outcome.Err(TagError.Security(SecurityFailure.PackMismatch)), result)
        assertNull(store.credentialFor(SPEC_UID), "a tag that may be a clone must not keep the password")
        assertEquals(1, tag.authFrames().size)
    }

    @Test
    fun aMatchingExpectedPackLetsTheOperationContinue() = runTest {
        val tag = protectedTag()
        store.remember(SPEC_UID, right, pack = "AB CD".hex())

        assertEquals(Outcome.Ok(Unit), TagUnlock.ensureUnlocked(tag, SPEC_UID, store))
        assertFalse(store.credentialFor(SPEC_UID) == null, "a good password stays remembered")
        assertEquals(1, tag.authFrames().size)
    }

    @Test
    fun theFailureTextsNeverContainThePassword() = runTest {
        val tag = protectedTag()
        store.remember(SPEC_UID, wrong)

        val result = TagUnlock.ensureUnlocked(tag, SPEC_UID, store)

        assertFalse("00000001" in result.toString().replace(" ", ""), result.toString())
    }
}
