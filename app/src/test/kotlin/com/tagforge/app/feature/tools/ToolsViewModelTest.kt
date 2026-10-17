package com.tagforge.app.feature.tools

import app.cash.turbine.test
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeSettingsStore
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.FixedClock
import com.tagforge.domain.fakes.InMemoryHistoryRepository
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryOutcome
import com.tagforge.domain.history.RecordHistoryUseCase
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.Type2Tlv
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * TT-01 Erase and TT-02 Format as the DESTRUCTIVE protocol (G5): confirm -> arm -> execute. Cancelling, going back or
 * dismissing at any point before the tag is touched means ZERO writes on the FakeNtag21x.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolsViewModelTest {

    private val availability = MutableStateFlow(NfcAvailability.ENABLED)
    private val settings = FakeSettingsStore()
    private val history = InMemoryHistoryRepository()
    private val clock = FixedClock(Instant.fromEpochMilliseconds(7_000))
    private val message = NdefMessage(listOf(NdefRecord.uri("https://example.com")))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.coordinator() = ScanCoordinator(this, StandardTestDispatcher(testScheduler))

    private fun viewModel(coordinator: ScanCoordinator) = ToolsViewModel(
        coordinator = coordinator,
        recordHistory = RecordHistoryUseCase(history, settings, clock),
        availability = availability,
    )

    private fun tagWithMessage(model: NtagModel = NtagModel.NTAG213): FakeNtag21x =
        FakeNtag21x(model).also { runBlocking { FakeTagSession.ntag(it).writeNdef(message) } }

    private fun session(tag: FakeNtag21x) = FakeTagSession.ntag(tag)

    private fun areaOf(tag: FakeNtag21x): Type2Tlv.Area =
        (Type2Tlv.unwrap(Bytes.concat((tag.model.userStart..tag.model.userEnd).map(tag::page))) as Outcome.Ok).value

    // ---- the confirmation comes first -----------------------------------------------------------------------------

    @Test
    fun requestingAnEraseOnlyAsksAndArmsNothing() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = tagWithMessage()
        val writes = tag.writeCount

        vm.onRequest(DestructiveTool.ERASE)
        coordinator.onTagDiscovered(session(tag))
        advanceUntilIdle()

        assertEquals(DestructiveTool.ERASE, vm.uiState.value.confirm)
        assertNull(vm.uiState.value.sheet)
        assertEquals(writes, tag.writeCount, "a tag tapped while the question is open is not touched")
    }

    @Test
    fun cancellingTheQuestionWritesNothingEvenIfATagIsTappedLater() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = tagWithMessage()
        val writes = tag.writeCount

        vm.onRequest(DestructiveTool.FORMAT)
        vm.onCancelConfirm()
        coordinator.onTagDiscovered(session(tag))
        advanceUntilIdle()

        assertNull(vm.uiState.value.confirm)
        assertNull(vm.uiState.value.sheet)
        assertEquals(writes, tag.writeCount)
        assertEquals(0, history.observe().first().size)
    }

    @Test
    fun backWhileTheQuestionIsOpenClosesItAndIsConsumed() = runTest {
        val vm = viewModel(coordinator())

        vm.onRequest(DestructiveTool.ERASE)
        assertTrue(vm.onBack(), "back belongs to the dialog")
        assertNull(vm.uiState.value.confirm)
        assertFalse(vm.onBack(), "nothing left to close: back goes to the navigation host")
    }

    @Test
    fun noQuestionIsAskedWhenNfcCannotBeUsed() = runTest {
        val vm = viewModel(coordinator())
        for (unusable in listOf(NfcAvailability.DISABLED, NfcAvailability.UNSUPPORTED)) {
            availability.value = unusable

            vm.onRequest(DestructiveTool.ERASE)

            assertNull(vm.uiState.value.confirm, "$unusable")
            assertFalse(vm.uiState.value.canStart, "$unusable")
        }
        availability.value = NfcAvailability.ENABLED
        assertTrue(vm.uiState.value.canStart)
    }

    // ---- erase ---------------------------------------------------------------------------------------------------------

    @Test
    fun confirmingArmsTheSheetAndTheNextTagIsErasedAndLogged() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = tagWithMessage()

        vm.events.test {
            vm.onRequest(DestructiveTool.ERASE)
            vm.onConfirm()
            assertNull(vm.uiState.value.confirm)
            assertEquals(ToolSheet.Waiting(DestructiveTool.ERASE), vm.uiState.value.sheet)

            coordinator.onTagDiscovered(session(tag))
            advanceUntilIdle()

            assertEquals(ToolsEvent.TagDetected, awaitItem())
            assertEquals(ToolsEvent.Succeeded, awaitItem())
            expectNoEvents()
        }

        assertEquals(ToolSheet.Success(DestructiveTool.ERASE, changed = true), vm.uiState.value.sheet)
        assertTrue(areaOf(tag).message.isEmpty())
        assertEquals("34 03 00 FE".hex(), tag.page(5))
        val entry = history.observe().first().single()
        assertEquals(HistoryKind.ERASE, entry.kind)
        assertEquals(HistoryOutcome.SUCCESS, entry.outcome)
        assertEquals(tag.uid, entry.uid)
        assertEquals("", entry.summary, "what was erased is deliberately not kept")
        assertNull(entry.ndef)
    }

    @Test
    fun erasingAnAlreadyEmptyTagSucceedsWithoutChangingAnything() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = FakeNtag21x(NtagModel.NTAG215)

        vm.onRequest(DestructiveTool.ERASE)
        vm.onConfirm()
        coordinator.onTagDiscovered(session(tag))
        advanceUntilIdle()

        assertEquals(ToolSheet.Success(DestructiveTool.ERASE, changed = false), vm.uiState.value.sheet)
        assertEquals(0, tag.writeCount)
    }

    @Test
    fun dismissingTheWaitingSheetDisarmsSoTheTagIsNeverTouched() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = tagWithMessage()
        val writes = tag.writeCount

        vm.onRequest(DestructiveTool.ERASE)
        vm.onConfirm()
        vm.onSheetDismissed()
        coordinator.onTagDiscovered(session(tag))
        advanceUntilIdle()

        assertNull(vm.uiState.value.sheet)
        assertEquals(writes, tag.writeCount)
        assertEquals(0, history.observe().first().size, "a cancelled erase is not an attempt")
    }

    @Test
    fun leavingTheScreenWhileWaitingDisarmsAndClearsEverything() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = tagWithMessage()
        val writes = tag.writeCount

        vm.onRequest(DestructiveTool.ERASE)
        vm.onConfirm()
        vm.onHidden()
        coordinator.onTagDiscovered(session(tag))
        advanceUntilIdle()

        assertNull(vm.uiState.value.sheet)
        assertNull(vm.uiState.value.confirm)
        assertEquals(writes, tag.writeCount)
    }

    @Test
    fun leavingTheScreenWithAQuestionOpenClosesItWithoutArming() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = tagWithMessage()
        val writes = tag.writeCount

        vm.onRequest(DestructiveTool.FORMAT)
        vm.onHidden()
        coordinator.onTagDiscovered(session(tag))
        advanceUntilIdle()

        assertNull(vm.uiState.value.confirm)
        assertEquals(writes, tag.writeCount)
    }

    /** A session whose NDEF read blocks until the test opens the gate, freezing the operation mid-flight. */
    private class GatedSession(private val inner: FakeTagSession, val gate: CompletableDeferred<Unit>) : TagSession by inner {
        override suspend fun identify() = inner.identify().also { gate.await() }
    }

    @Test
    fun theSheetCannotBeDismissedWhileTheTagIsBeingChanged() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = tagWithMessage()
        val gated = GatedSession(session(tag), CompletableDeferred())

        vm.onRequest(DestructiveTool.ERASE)
        vm.onConfirm()
        coordinator.onTagDiscovered(gated)
        runCurrent()
        val working = vm.uiState.value.sheet
        assertIs<ToolSheet.Working>(working)

        vm.onSheetDismissed()
        vm.onHidden()
        assertEquals(working, vm.uiState.value.sheet, "aborting mid-erase would leave the tag half done")

        gated.gate.complete(Unit)
        advanceUntilIdle()
        assertIs<ToolSheet.Success>(vm.uiState.value.sheet)
        assertTrue(areaOf(tag).message.isEmpty())
    }

    // ---- failures (G4) -----------------------------------------------------------------------------------------------------

    @Test
    fun aTagPulledAwayMidEraseIsAFailureWithRetryAndLoggedAsTagLost() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = tagWithMessage()
        tag.loseTagAfter(tag.writeCount + 1)

        vm.events.test {
            vm.onRequest(DestructiveTool.ERASE)
            vm.onConfirm()
            coordinator.onTagDiscovered(session(tag))
            advanceUntilIdle()
            assertEquals(ToolsEvent.TagDetected, awaitItem())
            assertEquals(ToolsEvent.Failed, awaitItem())
            expectNoEvents()
        }

        val sheet = vm.uiState.value.sheet
        assertIs<ToolSheet.Failure>(sheet)
        assertEquals(TagError.TagLost, sheet.error)
        assertTrue(sheet.canRetry, "bring the tag back and retry")
        assertEquals(DestructiveTool.ERASE, sheet.tool)
        assertEquals(5, areaOf(tag).leadingBytes, "the Lock Control TLV survived")
        val entry = history.observe().first().single()
        assertEquals(HistoryKind.ERASE, entry.kind)
        assertEquals(HistoryOutcome.FAILED_TAGLOST, entry.outcome)
    }

    @Test
    fun retryingAfterATagLostArmsAgainWithoutAskingTwiceAndFinishesOnTheNextTap() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = tagWithMessage()
        tag.loseTagAfter(tag.writeCount + 1)
        vm.onRequest(DestructiveTool.ERASE)
        vm.onConfirm()
        coordinator.onTagDiscovered(session(tag))
        advanceUntilIdle()
        assertIs<ToolSheet.Failure>(vm.uiState.value.sheet)

        vm.onRetry()
        assertEquals(ToolSheet.Waiting(DestructiveTool.ERASE), vm.uiState.value.sheet)
        assertNull(vm.uiState.value.confirm)
        val second = tagWithMessage()
        coordinator.onTagDiscovered(session(second))
        advanceUntilIdle()

        assertIs<ToolSheet.Success>(vm.uiState.value.sheet)
        assertEquals(listOf(HistoryOutcome.SUCCESS, HistoryOutcome.FAILED_TAGLOST), history.observe().first().map { it.outcome })
    }

    @Test
    fun aLockedTagIsReadOnlyWithNoRetryAndZeroWrites() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = tagWithMessage()
        tag.setPage(3, "E1 10 12 0F".hex())
        val writes = tag.writeCount

        vm.onRequest(DestructiveTool.FORMAT)
        vm.onConfirm()
        coordinator.onTagDiscovered(session(tag))
        advanceUntilIdle()

        val sheet = vm.uiState.value.sheet
        assertIs<ToolSheet.Failure>(sheet)
        assertEquals(TagError.ReadOnly, sheet.error)
        assertFalse(sheet.canRetry)
        assertEquals(writes, tag.writeCount)
        assertEquals(HistoryOutcome.FAILED_READ_ONLY, history.observe().first().single().outcome)
    }

    @Test
    fun mifareClassicIsExplainedAndNotRetryable() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)

        vm.onRequest(DestructiveTool.ERASE)
        vm.onConfirm()
        coordinator.onTagDiscovered(FakeTagSession.withoutNtag(TagFamily.MIFARE_CLASSIC))
        advanceUntilIdle()

        val sheet = vm.uiState.value.sheet
        assertIs<ToolSheet.Failure>(sheet)
        assertEquals(TagError.Unsupported(TagError.Unsupported.MIFARE_CLASSIC), sheet.error)
        assertFalse(sheet.canRetry)
    }

    // ---- format ---------------------------------------------------------------------------------------------------------------

    @Test
    fun formattingRepairsAZeroedTagAndIsLoggedAsFormat() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = FakeNtag21x(NtagModel.NTAG213)
        for (page in 4..39) tag.setPage(page, "00 00 00 00".hex())

        vm.onRequest(DestructiveTool.FORMAT)
        vm.onConfirm()
        coordinator.onTagDiscovered(session(tag))
        advanceUntilIdle()

        assertEquals(ToolSheet.Success(DestructiveTool.FORMAT, changed = true), vm.uiState.value.sheet)
        assertEquals("01 03 A0 0C".hex(), tag.page(4))
        assertEquals("34 03 00 FE".hex(), tag.page(5))
        assertEquals(HistoryKind.FORMAT, history.observe().first().single().kind)
    }

    @Test
    fun doneClosesTheResultSheetAndASecondTapIsIgnored() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onRequest(DestructiveTool.ERASE)
        vm.onConfirm()
        coordinator.onTagDiscovered(session(tagWithMessage()))
        advanceUntilIdle()

        vm.onSheetDismissed()
        val another = tagWithMessage()
        val writes = another.writeCount
        coordinator.onTagDiscovered(session(another))
        advanceUntilIdle()

        assertNull(vm.uiState.value.sheet)
        assertEquals(writes, another.writeCount)
    }
}
