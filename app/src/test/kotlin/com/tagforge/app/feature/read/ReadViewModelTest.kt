package com.tagforge.app.feature.read

import app.cash.turbine.test
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.RecordHistoryUseCase
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeSettingsStore
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.FixedClock
import com.tagforge.domain.fakes.InMemoryHistoryRepository
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
import com.tagforge.domain.task.RingerMode
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.task.TaskError
import com.tagforge.domain.task.TaskMessageBuilder
import com.tagforge.domain.task.TaskPayload
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertNull

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

    private val history = InMemoryHistoryRepository()

    private fun TestScope.viewModel(
        coordinator: ScanCoordinator = coordinator(),
        operation: TagOperation<TagScan> = ReadFullTagUseCase(),
    ) = ReadViewModel(coordinator, operation, availability, RecordHistoryUseCase(history, FakeSettingsStore(), FixedClock()))

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

    // ---- history (HT-01 "Read logged") ------------------------------------------------------------------

    @Test
    fun aSuccessfulReadIsLoggedWithTheTagUidAndRawNdef() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onVisible()
        vm.uiState.test {
            awaitItem()
            coordinator.onTagDiscovered(ntagWithText("hello"))
            advanceUntilIdle()
            expectMostRecentItem()
        }

        val entry = history.observe().first().single()
        assertEquals(HistoryKind.READ, entry.kind)
        assertEquals(FakeNtag21x.DEFAULT_UID, entry.uid)
        assertEquals("hello", entry.summary)
        assertEquals(com.tagforge.domain.ndef.DefaultNdefCodec.encode(NdefMessage(listOf(NdefRecord.text("hello", "en")))), entry.ndef)
    }

    @Test
    fun eachTagThatIsReadGetsItsOwnEntry() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onVisible()
        vm.uiState.test {
            awaitItem()
            coordinator.onTagDiscovered(ntagWithText("one"))
            advanceUntilIdle()
            coordinator.onTagDiscovered(ntagWithText("two"))
            advanceUntilIdle()
            expectMostRecentItem()
        }

        assertEquals(listOf("two", "one"), history.observe().first().map { it.summary })
    }

    @Test
    fun aFailedReadLeavesNoHistoryEntry() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onVisible()
        val tag = FakeNtag21x(NtagModel.NTAG213).apply { failNext(TagError.TagLost) }
        vm.uiState.test {
            awaitItem()
            coordinator.onTagDiscovered(FakeTagSession.ntag(tag))
            advanceUntilIdle()
            expectMostRecentItem()
        }

        assertEquals(0, history.observe().first().size)
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
    fun unlockOpensTheSecurityScreenForTheProtectedTag() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onUnlockClicked()
            assertEquals(ReadEvent.OpenUnlock, awaitItem())
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

    // ---- ownership: leaving must not cancel the screen we navigated to ------------------------------------------

    @Test
    fun hidingDoesNotCancelTheOperationOfTheScreenThatArrivedFirst() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onVisible()
        val next = coordinator.arm(com.tagforge.domain.read.ReadTagUseCase())   // navigation arms the new screen before the old one stops

        vm.onHidden()
        coordinator.onTagDiscovered(ntagWithText("hi"))
        advanceUntilIdle()

        assertIs<com.tagforge.domain.scan.ScanState.Success<*>>(next.value, "the arriving screen still got its tag")
    }

    // ---- Tag Forge task tags (TK-05 "Foreground scan") -----------------------------------------------------------

    private val taskPayload = TaskPayload("Night", listOf(TaskAction.SetRinger(RingerMode.VIBRATE), TaskAction.OpenUrl("https://example.com")))

    private fun ntagWithMessage(message: NdefMessage): FakeTagSession {
        val session = FakeTagSession.ntag(FakeNtag21x(NtagModel.NTAG215))
        kotlinx.coroutines.runBlocking { session.writeNdef(message) }
        return session
    }

    private fun taskTag(): FakeTagSession =
        ntagWithMessage((TaskMessageBuilder.build(taskPayload, "com.tagforge.app") as Outcome.Ok).value)

    @Test
    fun aTaskTagIsRecognisedAsATaskCardAndNothingRunsOrOpensByItself() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onVisible()
        vm.events.test {
            vm.uiState.test {
                awaitItem()
                coordinator.onTagDiscovered(taskTag())
                advanceUntilIdle()

                val success = expectMostRecentItem()
                assertIs<ReadUiState.Success>(success)
                assertEquals(TaskOnTag.Valid(taskPayload), success.task)
            }
            assertEquals(ReadEvent.TagDetected, awaitItem())
            assertEquals(ReadEvent.Succeeded, awaitItem())
            expectNoEvents()   // no OpenTaskRunner: the user has to press Run
        }
    }

    @Test
    fun runOpensTheRunnerFlowExactlyOncePerScan() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onVisible()
        val expectedBytes = (TaskMessageBuilder.build(taskPayload, "com.tagforge.app") as Outcome.Ok).value
            .let(com.tagforge.domain.ndef.DefaultNdefCodec::encode)
        vm.uiState.test {
            awaitItem()
            coordinator.onTagDiscovered(taskTag())
            advanceUntilIdle()
            expectMostRecentItem()

            vm.events.test {
                skipItems(2)   // TagDetected, Succeeded
                vm.onRunTask()
                vm.onRunTask()   // a double tap must not open two sheets

                assertEquals(ReadEvent.OpenTaskRunner(expectedBytes), awaitItem())
                expectNoEvents()
            }
        }
    }

    @Test
    fun scanningTheNextTaskTagAllowsRunAgain() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onVisible()
        vm.uiState.test {
            awaitItem()
            coordinator.onTagDiscovered(taskTag())
            advanceUntilIdle()
            expectMostRecentItem()
            vm.events.test {
                skipItems(2)
                vm.onRunTask()
                assertIs<ReadEvent.OpenTaskRunner>(awaitItem())

                coordinator.onTagDiscovered(taskTag())
                advanceUntilIdle()
                skipItems(2)
                vm.onRunTask()
                assertIs<ReadEvent.OpenTaskRunner>(awaitItem())
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anOrdinaryTagHasNoTaskCardAndRunDoesNothing() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onVisible()
        vm.uiState.test {
            awaitItem()
            coordinator.onTagDiscovered(ntagWithText("hello"))
            advanceUntilIdle()

            val success = expectMostRecentItem()
            assertIs<ReadUiState.Success>(success)
            assertNull(success.task)

            vm.events.test {
                skipItems(2)
                vm.onRunTask()
                expectNoEvents()
            }
        }
    }

    @Test
    fun aHostileTaskTagIsShownAsInvalidAndCannotBeRun() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onVisible()
        val hostile = ntagWithMessage(NdefMessage(listOf(NdefRecord.mime("application/x-tf", Bytes.of(0x20, 0x00)))))
        vm.uiState.test {
            awaitItem()
            coordinator.onTagDiscovered(hostile)
            advanceUntilIdle()

            val success = expectMostRecentItem()
            assertIs<ReadUiState.Success>(success)
            assertEquals(TaskOnTag.Invalid(TaskError.UnsupportedVersion(2)), success.task)

            vm.events.test {
                skipItems(2)
                vm.onRunTask()
                expectNoEvents()
            }
        }
    }
}
