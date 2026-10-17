package com.tagforge.app.feature.tools

import app.cash.turbine.test
import com.tagforge.app.feature.write.OverwritePrompt
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeSettingsStore
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.FixedClock
import com.tagforge.domain.fakes.InMemoryHistoryRepository
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryOutcome
import com.tagforge.domain.history.RecordHistoryUseCase
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.Type2Tlv
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** TT-04: read the source, confirm the target, write, verify; G5 for the overwrite question. */
@OptIn(ExperimentalCoroutinesApi::class)
class CloneViewModelTest {

    private val availability = MutableStateFlow(NfcAvailability.ENABLED)
    private val settings = FakeSettingsStore()
    private val history = InMemoryHistoryRepository()
    private val clock = FixedClock(Instant.fromEpochMilliseconds(9_000))
    private val message = NdefMessage(listOf(NdefRecord.uri("https://example.com")))
    private val sourceUid = "04 11 22 33 44 55 66".hex()
    private val targetUid = "04 AA BB CC DD EE FF".hex()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.coordinator() = ScanCoordinator(this, StandardTestDispatcher(testScheduler))

    private fun viewModel(coordinator: ScanCoordinator) = CloneViewModel(
        coordinator = coordinator,
        recordHistory = RecordHistoryUseCase(history, settings, clock),
        availability = availability,
    )

    private fun tag(model: NtagModel, uid: Bytes, content: NdefMessage? = null): FakeNtag21x =
        FakeNtag21x(model, uid).also { t -> content?.let { runBlocking { FakeTagSession.ntag(t).writeNdef(it) } } }

    private fun session(tag: FakeNtag21x) = FakeTagSession.ntag(tag)

    private fun contentOf(tag: FakeNtag21x): Bytes =
        (Type2Tlv.unwrap(Bytes.concat((tag.model.userStart..tag.model.userEnd).map(tag::page))) as Outcome.Ok).value.message

    /** Starts a clone and taps [source], leaving the view model on the "scan the destination" step. */
    private fun TestScope.capture(vm: CloneViewModel, coordinator: ScanCoordinator, source: FakeNtag21x) {
        vm.onStart()
        coordinator.onTagDiscovered(session(source))
        advanceUntilIdle()
    }

    // ---- step 1: the source -----------------------------------------------------------------------------------------

    @Test
    fun startingArmsTheSourceReadAndShowsTheWaitingSheet() = runTest {
        val vm = viewModel(coordinator())

        assertEquals(CloneStep.SOURCE, vm.uiState.value.step)
        vm.onStart()

        assertEquals(CloneSheet.Waiting(CloneStep.SOURCE), vm.uiState.value.sheet)
    }

    @Test
    fun capturingTheSourceMovesToTheDestinationStepWithoutWritingAnything() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val source = tag(NtagModel.NTAG213, sourceUid, message)
        val writes = source.writeCount

        vm.events.test {
            capture(vm, coordinator, source)
            assertEquals(CloneEvent.TagDetected, awaitItem())
            assertEquals(CloneEvent.SourceCaptured, awaitItem())
            expectNoEvents()
        }

        val state = vm.uiState.value
        assertEquals(CloneStep.TARGET, state.step)
        assertNull(state.sheet)
        val summary = assertNotNull(state.source)
        assertEquals("NTAG213", summary.chip)
        assertEquals(sourceUid, summary.uid)
        assertEquals(DefaultNdefCodec.encode(message).size, summary.messageSize)
        assertEquals("https://example.com", summary.summary)
        assertFalse(summary.withUserPages)
        assertEquals(writes, source.writeCount, "the source is only read")
    }

    @Test
    fun anEmptySourceFailsOnTheSourceStepAndStaysThere() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)

        capture(vm, coordinator, tag(NtagModel.NTAG213, sourceUid))

        val sheet = vm.uiState.value.sheet
        assertIs<CloneSheet.Failure>(sheet)
        assertEquals(CloneStep.SOURCE, sheet.step)
        assertEquals(TagError.NotNdef, sheet.error)
        assertEquals(CloneStep.SOURCE, vm.uiState.value.step)
        assertNull(vm.uiState.value.source)
    }

    @Test
    fun aMifareClassicSourceIsExplainedAndNotRetryable() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)

        vm.onStart()
        coordinator.onTagDiscovered(FakeTagSession.withoutNtag(TagFamily.MIFARE_CLASSIC))
        advanceUntilIdle()

        val sheet = vm.uiState.value.sheet
        assertIs<CloneSheet.Failure>(sheet)
        assertEquals(TagError.Unsupported(TagError.Unsupported.MIFARE_CLASSIC), sheet.error)
        assertFalse(sheet.canRetry)
    }

    @Test
    fun nothingStartsWhileNfcIsOff() = runTest {
        val vm = viewModel(coordinator())
        availability.value = NfcAvailability.DISABLED

        vm.onStart()

        assertNull(vm.uiState.value.sheet)
        assertFalse(vm.uiState.value.canStart)
    }

    // ---- step 2: the destination --------------------------------------------------------------------------------------

    @Test
    fun cloningToABlankLargerTagVerifiesAndIsLoggedAsClone() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        capture(vm, coordinator, tag(NtagModel.NTAG213, sourceUid, message))
        val target = tag(NtagModel.NTAG215, targetUid)

        vm.events.test {
            skipItems(2)   // the source step's TagDetected and SourceCaptured
            vm.onScanTarget()
            assertEquals(CloneSheet.Waiting(CloneStep.TARGET), vm.uiState.value.sheet)
            coordinator.onTagDiscovered(session(target))
            advanceUntilIdle()
            assertEquals(CloneEvent.TagDetected, awaitItem())
            assertEquals(CloneEvent.Succeeded, awaitItem())
            expectNoEvents()
        }

        val sheet = vm.uiState.value.sheet
        assertIs<CloneSheet.Success>(sheet)
        assertEquals(targetUid, sheet.report.target.uid)
        assertEquals(DefaultNdefCodec.encode(message), contentOf(target))
        val entry = history.observe().first().single()
        assertEquals(HistoryKind.CLONE, entry.kind)
        assertEquals(HistoryOutcome.SUCCESS, entry.outcome)
        assertEquals(targetUid, entry.uid)
    }

    @Test
    fun theSourceTagTappedAgainIsRejectedAndAnotherTagWorksAfterRetry() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val source = tag(NtagModel.NTAG213, sourceUid, message)
        capture(vm, coordinator, source)
        val writes = source.writeCount

        vm.onScanTarget()
        coordinator.onTagDiscovered(session(source))
        advanceUntilIdle()

        val sheet = vm.uiState.value.sheet
        assertIs<CloneSheet.Failure>(sheet)
        assertEquals(TagError.SameTag, sheet.error)
        assertTrue(sheet.canRetry)
        assertEquals(writes, source.writeCount)

        vm.onRetry()
        assertEquals(CloneSheet.Waiting(CloneStep.TARGET), vm.uiState.value.sheet)
        val other = tag(NtagModel.NTAG216, targetUid)
        coordinator.onTagDiscovered(session(other))
        advanceUntilIdle()
        assertIs<CloneSheet.Success>(vm.uiState.value.sheet)
    }

    @Test
    fun aDestinationThatIsTooSmallFailsBeforeAnyWrite() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val big = NdefMessage(listOf(NdefRecord.text("x".repeat(290), "en")))
        capture(vm, coordinator, tag(NtagModel.NTAG215, sourceUid, big))
        val target = tag(NtagModel.NTAG213, targetUid)

        vm.onScanTarget()
        coordinator.onTagDiscovered(session(target))
        advanceUntilIdle()

        val sheet = vm.uiState.value.sheet
        assertIs<CloneSheet.Failure>(sheet)
        assertIs<TagError.CapacityExceeded>(sheet.error)
        assertEquals(0, target.writeCount)
        assertEquals(HistoryOutcome.FAILED_CAPACITY, history.observe().first().single().outcome)
    }

    // ---- G5: the overwrite question ------------------------------------------------------------------------------------

    private val old = NdefMessage(listOf(NdefRecord.text("keep me", "en")))

    @Test
    fun aNonEmptyDestinationAsksAndDecliningWritesNothingAndIsNotLogged() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        capture(vm, coordinator, tag(NtagModel.NTAG213, sourceUid, message))
        val target = tag(NtagModel.NTAG215, targetUid, old)
        val writes = target.writeCount

        vm.onScanTarget()
        coordinator.onTagDiscovered(session(target))
        runCurrent()
        assertEquals(OverwritePrompt("keep me"), vm.uiState.value.overwrite)

        vm.onOverwriteDecision(false)
        advanceUntilIdle()

        assertNull(vm.uiState.value.sheet)
        assertNull(vm.uiState.value.overwrite)
        assertEquals(writes, target.writeCount)
        assertEquals(0, history.observe().first().size, "a declined overwrite is not an attempt")
    }

    @Test
    fun acceptingTheOverwriteReplacesTheContent() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        capture(vm, coordinator, tag(NtagModel.NTAG213, sourceUid, message))
        val target = tag(NtagModel.NTAG215, targetUid, old)

        vm.onScanTarget()
        coordinator.onTagDiscovered(session(target))
        runCurrent()
        vm.onOverwriteDecision(true)
        advanceUntilIdle()

        assertIs<CloneSheet.Success>(vm.uiState.value.sheet)
        assertEquals(DefaultNdefCodec.encode(message), contentOf(target))
    }

    @Test
    fun dismissingTheSheetWhileTheQuestionIsOpenCountsAsDeclining() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        capture(vm, coordinator, tag(NtagModel.NTAG213, sourceUid, message))
        val target = tag(NtagModel.NTAG215, targetUid, old)
        val writes = target.writeCount

        vm.onScanTarget()
        coordinator.onTagDiscovered(session(target))
        runCurrent()
        vm.onSheetDismissed()
        advanceUntilIdle()

        assertEquals(writes, target.writeCount)
        assertNull(vm.uiState.value.sheet)
        assertNull(vm.uiState.value.overwrite)
    }

    // ---- failures and lifecycle ------------------------------------------------------------------------------------------

    @Test
    fun aTagPulledAwayMidCloneIsAFailureLoggedAsTagLostAndTheTargetStaysParsable() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        capture(vm, coordinator, tag(NtagModel.NTAG213, sourceUid, message))
        val target = tag(NtagModel.NTAG213, targetUid)
        target.loseTagAfter(2)

        vm.events.test {
            skipItems(2)   // the source step's TagDetected and SourceCaptured
            vm.onScanTarget()
            coordinator.onTagDiscovered(session(target))
            advanceUntilIdle()
            assertEquals(CloneEvent.TagDetected, awaitItem())
            assertEquals(CloneEvent.Failed, awaitItem())
            expectNoEvents()
        }

        val sheet = vm.uiState.value.sheet
        assertIs<CloneSheet.Failure>(sheet)
        assertEquals(TagError.TagLost, sheet.error)
        assertTrue(sheet.canRetry)
        assertEquals(HistoryOutcome.FAILED_TAGLOST, history.observe().first().single().outcome)
        val content = contentOf(target)
        assertTrue(content.isEmpty() || content == DefaultNdefCodec.encode(message), "never a half message")
    }

    @Test
    fun copyingUserPagesReproducesThemOnTheSameChipModel() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val source = tag(NtagModel.NTAG215, sourceUid, message).also { it.setPage(60, "DE AD BE EF".hex()) }
        vm.onCopyUserPagesChanged(true)
        capture(vm, coordinator, source)
        assertTrue(vm.uiState.value.source!!.withUserPages)
        val target = tag(NtagModel.NTAG215, targetUid)

        vm.onScanTarget()
        coordinator.onTagDiscovered(session(target))
        advanceUntilIdle()

        val sheet = vm.uiState.value.sheet
        assertIs<CloneSheet.Success>(sheet)
        assertTrue(sheet.report.copiedUserPages)
        assertEquals(source.page(60), target.page(60))
    }

    @Test
    fun userPagesToADifferentModelAreRefusedWithAnExplanationAndNoWrites() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onCopyUserPagesChanged(true)
        capture(vm, coordinator, tag(NtagModel.NTAG213, sourceUid, message))
        val target = tag(NtagModel.NTAG215, targetUid)

        vm.onScanTarget()
        coordinator.onTagDiscovered(session(target))
        advanceUntilIdle()

        val sheet = vm.uiState.value.sheet
        assertIs<CloneSheet.Failure>(sheet)
        assertEquals(TagError.Unsupported(TagError.Unsupported.USER_PAGES_MODEL), sheet.error)
        assertFalse(sheet.canRetry)
        assertEquals(0, target.writeCount)
    }

    @Test
    fun backOnTheDestinationStepDiscardsTheSourceAndIsConsumed() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        capture(vm, coordinator, tag(NtagModel.NTAG213, sourceUid, message))

        assertTrue(vm.onBack())

        assertEquals(CloneStep.SOURCE, vm.uiState.value.step)
        assertNull(vm.uiState.value.source)
        val target = tag(NtagModel.NTAG215, targetUid)
        coordinator.onTagDiscovered(session(target))
        advanceUntilIdle()
        assertEquals(0, target.writeCount, "nothing is armed after discarding the source")
        assertFalse(vm.onBack(), "on the first step back belongs to the navigation host")
    }

    @Test
    fun leavingTheScreenWhileWaitingDisarmsButKeepsTheCapturedSource() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        capture(vm, coordinator, tag(NtagModel.NTAG213, sourceUid, message))
        vm.onScanTarget()
        vm.onHidden()
        val target = tag(NtagModel.NTAG215, targetUid)

        coordinator.onTagDiscovered(session(target))
        advanceUntilIdle()

        assertNull(vm.uiState.value.sheet)
        assertEquals(0, target.writeCount)
        assertNotNull(vm.uiState.value.source, "coming back, the user can continue where they were")
    }

    @Test
    fun resettingReturnsToTheFirstStepFromAResult() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        capture(vm, coordinator, tag(NtagModel.NTAG213, sourceUid, message))
        vm.onScanTarget()
        coordinator.onTagDiscovered(session(tag(NtagModel.NTAG215, targetUid)))
        advanceUntilIdle()
        assertIs<CloneSheet.Success>(vm.uiState.value.sheet)

        vm.onReset()

        assertNull(vm.uiState.value.sheet)
        assertNull(vm.uiState.value.source)
        assertEquals(CloneStep.SOURCE, vm.uiState.value.step)
    }
}
