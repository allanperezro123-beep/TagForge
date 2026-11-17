package com.tagforge.domain.security

import com.tagforge.domain.fakes.FixedClock
import com.tagforge.domain.fakes.hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** WU3: a password the user typed once is kept in memory for a short while so the next operations can use it. */
class SessionCredentialsTest {

    private val clock = FixedClock()
    private val store = SessionCredentials(clock)
    private val uid = "04 A1 B2 C3 D4 E5 F6".hex()
    private val otherUid = "04 11 22 33 44 55 66".hex()
    private val password = Password.fromHex("12345678")!!

    @Test
    fun aRememberedPasswordIsReturnedForThatUidOnly() {
        store.remember(uid, password, pack = "AB CD".hex())

        val credential = assertNotNull(store.credentialFor(uid))
        assertEquals(password, credential.password)
        assertEquals("AB CD".hex(), credential.pack)
        assertNull(store.credentialFor(otherUid), "a different tag never receives it")
    }

    @Test
    fun theExpectedPackIsOptional() {
        store.remember(uid, password)

        assertNull(assertNotNull(store.credentialFor(uid)).pack)
    }

    @Test
    fun rememberingAgainReplacesThePreviousPasswordOfThatUid() {
        store.remember(uid, password, pack = "AB CD".hex())
        val newer = Password.fromHex("87654321")!!

        store.remember(uid, newer)

        val credential = assertNotNull(store.credentialFor(uid))
        assertEquals(newer, credential.password)
        assertNull(credential.pack, "the PACK of the old password does not survive")
    }

    @Test
    fun anEntryExpiresAfterTenMinutesAndIsDroppedOnAccess() {
        store.remember(uid, password)

        clock.advanceMillis(10 * 60_000L - 1)
        assertNotNull(store.credentialFor(uid), "still valid one millisecond before the limit")

        clock.advanceMillis(1)
        assertNull(store.credentialFor(uid), "expired exactly at 10 minutes")
        assertEquals(emptyList(), store.rememberedUids(), "the expired entry is gone, not just hidden")
    }

    @Test
    fun theTtlIsMeasuredFromTheMomentThePasswordWasRemembered() {
        store.remember(uid, password)
        clock.advanceMillis(9 * 60_000L)
        store.remember(otherUid, password)
        clock.advanceMillis(2 * 60_000L)

        assertNull(store.credentialFor(uid))
        assertNotNull(store.credentialFor(otherUid))
    }

    @Test
    fun forgetRemovesOneUidAndKeepsTheOthers() {
        store.remember(uid, password)
        store.remember(otherUid, password)

        store.forget(uid)

        assertNull(store.credentialFor(uid))
        assertNotNull(store.credentialFor(otherUid))
    }

    @Test
    fun forgetAllRemovesEverything() {
        store.remember(uid, password)
        store.remember(otherUid, password)

        store.forgetAll()

        assertEquals(emptyList(), store.rememberedUids())
        assertNull(store.credentialFor(uid))
        assertNull(store.credentialFor(otherUid))
    }

    @Test
    fun rememberedUidsListsTheLiveEntriesOnly() {
        store.remember(uid, password)
        clock.advanceMillis(6 * 60_000L)
        store.remember(otherUid, password)
        assertEquals(listOf(uid, otherUid), store.rememberedUids())

        clock.advanceMillis(5 * 60_000L)

        assertEquals(listOf(otherUid), store.rememberedUids())
        assertTrue(store.hasAny())
        clock.advanceMillis(6 * 60_000L)
        assertFalse(store.hasAny())
    }

    @Test
    fun liveCountFollowsRememberForgetAndExpiry() {
        assertEquals(0, store.liveCount.value)

        store.remember(uid, password)
        store.remember(otherUid, password)
        assertEquals(2, store.liveCount.value)

        store.forget(uid)
        assertEquals(1, store.liveCount.value)

        clock.advanceMillis(10 * 60_000L)
        store.rememberedUids()   // expiry is noticed when the store is touched
        assertEquals(0, store.liveCount.value)

        store.remember(uid, password)
        store.forgetAll()
        assertEquals(0, store.liveCount.value)
    }

    @Test
    fun noTextFormOfTheStoreOrOfACredentialRevealsTheSecrets() {
        store.remember(uid, password, pack = "AB CD".hex())
        val credential = assertNotNull(store.credentialFor(uid))

        for (text in listOf(store.toString(), credential.toString())) {
            assertFalse("12345678" in text.replace(" ", ""), text)
            assertFalse("ABCD" in text.replace(" ", ""), text)
        }
    }

    @Test
    fun liveCountTracksRemembersForgetsAndExpiry() {
        assertEquals(0, store.liveCount.value)

        store.remember(uid, password)
        store.remember(otherUid, password)
        assertEquals(2, store.liveCount.value)

        store.forget(uid)
        assertEquals(1, store.liveCount.value)

        clock.advanceMillis(10 * 60_000L)
        assertNull(store.credentialFor(otherUid))
        assertEquals(0, store.liveCount.value)

        store.remember(uid, password)
        assertEquals(1, store.liveCount.value)
        store.forgetAll()
        assertEquals(0, store.liveCount.value)
    }
}
