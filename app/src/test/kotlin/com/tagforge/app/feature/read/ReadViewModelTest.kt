package com.tagforge.app.feature.read

import app.cash.turbine.test
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.read.ReadFullTagUseCase
import com.tagforge.domain.read.TagScan
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ReadViewModelTest {

    private val availability = MutableStateFlow(NfcAvailability.ENABLED)

    @Before
    fun setUp() {
        // Unconfined main: stateIn / event collectors run eagerly, so assertions need no extra scheduling.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.coordinator() = ScanCoordinator(this, StandardTestDispatcher(testScheduler))

    private fun TestScope.viewModel(
        coordinator: ScanCoordinator = coordinator(),
        operation: TagOperation<TagScan> = ReadFullTagUseCase(),
    ) = ReadViewModel(coordinator, operation, availability)

    private fun ntagWithText(text: String): FakeTagSession {
        val session = FakeTagSession.ntag(FakeNtag21x(NtagModel.NTAG213))
        kotlinx.coroutines.runBlocking { session.writeNdef(NdefMessage(listOf(NdefRecord.text(text, "en")))) }
        return session
    }

    private class GatedOp(val gate: CompletableDeferred<Unit> = CompletableDeferred()) : TagOperation<TagScan> {
        override val kind = OperationKind.READ
        var executions = 0
        override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<TagScan> {
            executions++
            phase(OperationPhase.READING)
            gate.await()
            return ReadFullTagUseCase().execute(session, phase)
        }
    }

    // ---- availability -----------------------------------------------------------------------------

    @Test
    fun deviceWithoutNfcShowsUnsupported() = runTest {
        availability.value = NfcAvailability.UNSUPPORTED
        viewModel().uiState.test {
            assertEquals(ReadUiState.Unsupported, awaitItem())
        }
    }

    @Test
    fun disabledAdapterShowsDisabledThenFlipsToWaitingWhenEnabledAgain() = runTest {
        availability.value = NfcAvailability.DISABLED
        val vm = viewModel()
        vm.onVisible()
        vm.uiState.test {
            assertEquals(ReadUiState.Disabled, awaitItem())
            availability.value = NfcAvailability.ENABLED
            assertEquals(ReadUiState.Waiting, awaitItem())
        }
    }

    // ---- scan flow --------------------------------------------------------------------------------

    @Test
    fun visibleScreenIsWaitingAndTheTagBecomesASuccess() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.uiState.test {
            assertEquals(ReadUiState.Waiting, awaitItem())
            vm.onVisible()
            coordinator.onTagDiscovered(ntagWithText("hello"))
            advanceUntilIdle()

            val success = expectMostRecentItem()
            assertIs<ReadUiState.Success>(success)
            assertEquals(1, success.scan.readout.parsed.size)
        }
    }

    @Test
    fun readingStateIsVisibleWhileTheOperationRuns() = runTest {
        val coordinator = coordinator()
        val op = GatedOp()
        val vm = viewModel(coordinator, op)
        vm.onVisible()
        vm.uiState.test {
            assertEquals(ReadUiState.Waiting, awaitItem())
            coordinator.onTagDiscovered(ntagWithText("hi"))
            runCurrent()
            assertEquals(ReadUiState.Reading(OperationPhase.CONNECTING), awaitItem())
            assertEquals(ReadUiState.Reading(OperationPhase.READING), awaitItem())

            op.gate.complete(Unit)
            advanceUntilIdle()
            assertIs<ReadUiState.Success>(expectMostRecentItem())
        }
    }

    @Test
    fun tagLostShowsAnErrorAndRetryReturnsToWaiting() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onVisible()
        val tag = FakeNtag21x(NtagModel.NTAG213).apply { failNext(TagError.TagLost) }
        vm.uiState.test {
            assertEquals(ReadUiState.Waiting, awaitItem())
            coordinator.onTagDiscovered(FakeTagSession.ntag(tag))
            advanceUntilIdle()
            val error = expectMostRecentItem()
            assertIs<ReadUiState.Error>(error)
            assertEquals(TagError.TagLost, error.error)

            vm.onRetry()
            assertEquals(ReadUiState.Waiting, awaitItem())
        }
    }

    @Test
    fun passwordProtectedTagShowsProtectedWithItsIdentity() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onVisible()
        val tag = FakeNtag21x(NtagModel.NTAG215).apply {
            setPage(NtagModel.NTAG215.cfg0, "00 00 00 04".hex())
            setPage(NtagModel.NTAG215.cfg1, "80 00 00 00".hex())
        }
        vm.uiState.test {
            awaitItem()
            coordinator.onTagDiscovered(FakeTagSession.ntag(tag, identified = false))
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertIs<ReadUiState.Protected>(state)
            assertEquals(NtagModel.NTAG215, state.tag?.ntag)
        }
    }

    @Test
    fun aSecondTagReplacesThePreviousResult() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onVisible()
        vm.uiState.test {
            awaitItem()
            coordinator.onTagDiscovered(ntagWithText("one"))
            advanceUntilIdle()
            coordinator.onTagDiscovered(ntagWithText("two"))
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertIs<ReadUiState.Success>(state)
            assertEquals(NdefMessage(listOf(NdefRecord.text("two", "en"))), state.scan.readout.message)
        }
    }

    // ---- lifecycle --------------------------------------------------------------------------------

    @Test
    fun hidingTheScreenDisarmsSoTagsAreIgnored() = runTest {
        val coordinator = coordinator()
        val op = GatedOp().apply { gate.complete(Unit) }
        val vm = viewModel(coordinator, op)
        vm.onVisible()
        vm.onHidden()

        coordinator.onTagDiscovered(ntagWithText("ignored"))
        advanceUntilIdle()

        assertEquals(0, op.executions)
        assertEquals(com.tagforge.domain.scan.ScanState.Idle, coordinator.state.value)
    }

    @Test
    fun showingTheScreenAgainRearms() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.uiState.test {
            awaitItem()
            vm.onVisible()
            vm.onHidden()
            vm.onVisible()

            coordinator.onTagDiscovered(ntagWithText("again"))
            advanceUntilIdle()

            assertIs<ReadUiState.Success>(expectMostRecentItem())
        }
    }

    // ---- events (haptics / snackbars) -----------------------------------------------------------------

    @Test
    fun eventsAnnounceDetectionThenSuccess() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onVisible()
        vm.events.test {
            coordinator.onTagDiscovered(ntagWithText("x"))
            advanceUntilIdle()
            assertEquals(ReadEvent.TagDetected, awaitItem())
            assertEquals(ReadEvent.Succeeded, awaitItem())
        }
    }

    @Test
    fun eventsAnnounceFailures() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onVisible()
        val tag = FakeNtag21x(NtagModel.NTAG213).apply { failNext(TagError.TagLost) }
        vm.events.test {
            coordinator.onTagDiscovered(FakeTagSession.ntag(tag))
            advanceUntilIdle()
            assertEquals(ReadEvent.TagDetected, awaitItem())
            assertEquals(ReadEvent.Failed, awaitItem())
        }
    }

    @Test
    fun unlockIsAStubThatSaysSo() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onUnlockClicked()
            assertEquals(ReadEvent.UnlockUnavailable, awaitItem())
        }
    }

    @Test
    fun rearmingDoesNotReportAFailure() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onVisible()
        vm.events.test {
            vm.onRetry()   // re-arm cancels the previous arm with Failure(Cancelled): not a user-facing failure
            advanceUntilIdle()
            expectNoEvents()
        }
    }
}
