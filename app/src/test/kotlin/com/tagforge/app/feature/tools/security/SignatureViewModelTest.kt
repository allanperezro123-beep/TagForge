package com.tagforge.app.feature.tools.security

import com.tagforge.app.feature.tools.ToolReading
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.security.SecurityFailure
import com.tagforge.domain.security.SignatureReport
import com.tagforge.domain.security.SignatureVerification
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** NS-07: the signature screen shows the 32 bytes and the UID, says "not verified" and never claims a tag is genuine. */
@OptIn(ExperimentalCoroutinesApi::class)
class SignatureViewModelTest {

    private val availability = MutableStateFlow(NfcAvailability.ENABLED)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.coordinator() = ScanCoordinator(this, StandardTestDispatcher(testScheduler))

    @Test
    fun anNtagShowsItsSignatureAndUidAsNotVerified() = runTest {
        val coordinator = coordinator()
        val vm = SignatureViewModel(coordinator, availability)
        val tag = FakeNtag21x(NtagModel.NTAG215)
        tag.signature = Bytes.copyOf(ByteArray(32) { it.toByte() })

        vm.onVisible()
        coordinator.onTagDiscovered(FakeTagSession.ntag(tag))
        advanceUntilIdle()

        val reading = assertIs<ToolReading.Result<SignatureReport>>(vm.uiState.value.reading)
        assertEquals(tag.signature, reading.value.signature)
        assertEquals(tag.uid, reading.value.uid)
        assertEquals(SignatureVerification.NotVerified, reading.value.verification)
        assertEquals(emptyList(), tag.writeCommands)
    }

    @Test
    fun aTagThatIsNotAnNtagIsReportedAsUnsupportedWithoutAnyWrite() = runTest {
        val coordinator = coordinator()
        val vm = SignatureViewModel(coordinator, availability)

        vm.onVisible()
        coordinator.onTagDiscovered(FakeTagSession.withoutNtag(techs = setOf(com.tagforge.domain.tag.TagTech.NFC_A, com.tagforge.domain.tag.TagTech.MIFARE_ULTRALIGHT)))
        advanceUntilIdle()

        val reading = assertIs<ToolReading.Error>(vm.uiState.value.reading)
        assertEquals(TagError.Security(SecurityFailure.UnsupportedChip), reading.error)
    }
}
