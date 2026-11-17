package com.tagforge.domain.scan

import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.FixedClock
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.read.ReadTagUseCase
import com.tagforge.domain.read.TagReadout
import com.tagforge.domain.security.AuthenticateUseCase
import com.tagforge.domain.security.PROTECTED_TAG_PASSWORD
import com.tagforge.domain.security.Password
import com.tagforge.domain.security.SPEC_UID
import com.tagforge.domain.security.SessionCredentials
import com.tagforge.domain.security.passwordProtectedTag
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** WU3 wiring: the coordinator hands operations a session that unlocks by itself, except the operations that type their own password. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanCoordinatorUnlockTest {

    private val message = NdefMessage(listOf(NdefRecord.uri("https://example.com")))
    private val store = SessionCredentials(FixedClock())

    private fun TestScope.coordinator(credentials: SessionCredentials?) =
        ScanCoordinator(this, StandardTestDispatcher(testScheduler), credentials = credentials)

    @Test
    fun aReadOfAProtectedTagWorksOnceItsPasswordIsRemembered() = runTest {
        val tag = passwordProtectedTag(message)
        store.remember(SPEC_UID, PROTECTED_TAG_PASSWORD)
        val coordinator = coordinator(store)

        coordinator.arm(ReadTagUseCase())
        coordinator.onTagDiscovered(FakeTagSession.ntag(tag))
        advanceUntilIdle()

        val success = assertIs<ScanState.Success<*>>(coordinator.state.value)
        assertEquals(message, (success.value as TagReadout).message)
    }

    @Test
    fun withoutAStoreOrAPasswordTheTagStaysProtected() = runTest {
        for (credentials in listOf(null, store)) {
            val tag = passwordProtectedTag(message)
            val coordinator = coordinator(credentials)

            coordinator.arm(ReadTagUseCase())
            coordinator.onTagDiscovered(FakeTagSession.ntag(tag))
            advanceUntilIdle()

            assertEquals(TagError.AuthRequired, assertIs<ScanState.Failure>(coordinator.state.value).error, "store=$credentials")
        }
    }

    @Test
    fun aSecurityOperationSendsOnlyThePasswordTheUserTypedNeverTheRemembered() = runTest {
        val tag = passwordProtectedTag(message)
        store.remember(SPEC_UID, Password.fromHex("00000001")!!)   // an old, wrong password still in memory
        val coordinator = coordinator(store)

        coordinator.arm(AuthenticateUseCase(PROTECTED_TAG_PASSWORD))
        coordinator.onTagDiscovered(FakeTagSession.ntag(tag))
        advanceUntilIdle()

        assertIs<ScanState.Success<*>>(coordinator.state.value)
        assertEquals(listOf("1B 12 34 56 78".hex()), tag.commandLog.filter { it[0] == NtagCommands.PWD_AUTH }, "one frame: the typed password")
    }
}
