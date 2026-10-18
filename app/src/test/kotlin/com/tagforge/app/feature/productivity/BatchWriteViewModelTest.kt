package com.tagforge.app.feature.productivity

import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.exchange.BatchBlock
import com.tagforge.domain.exchange.BatchSummary
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeSettingsStore
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.FixedClock
import com.tagforge.domain.fakes.InMemoryHistoryRepository
import com.tagforge.domain.fakes.InMemoryTemplateRepository
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryOutcome
import com.tagforge.domain.history.RecordHistoryUseCase
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.ParsedRecord
import com.tagforge.domain.ndef.RecordInterpreter
import com.tagforge.domain.ndef.Type2Tlv
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.settings.AppSettings
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.template.Template
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

/** PR-01 end to end: a counter that moves only on verified writes, survives process death and never double-writes a tag. */
@OptIn(ExperimentalCoroutinesApi::class)
class BatchWriteViewModelTest {

    private val availability = MutableStateFlow(NfcAvailability.ENABLED)
    private val settings = FakeSettingsStore()
    private val history = InMemoryHistoryRepository()
    private val templates = InMemoryTemplateRepository()
    private val clock = FixedClock(Instant.fromEpochMilliseconds(5_000))

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.coordinator() = ScanCoordinator(this, StandardTestDispatcher(testScheduler))

    private fun TestScope.viewModel(coordinator: ScanCoordinator = coordinator(), store: FakeSettingsStore = settings) =
        BatchWriteViewModel(
            coordinator = coordinator,
            settings = store,
            recordHistory = RecordHistoryUseCase(history, store, clock),
            templates = templates,
            appPackage = "com.tagforge.app",
            availability = availability,
        )

    private suspend fun seedTemplate(): Long = seedTemplate(RecordSpec.Uri("https://x.co/t/{n}"))

    private suspend fun seedTemplate(vararg records: RecordSpec): Long =
        templates.upsert(Template(name = "Batch", records = records.toList(), createdAt = Instant.fromEpochMilliseconds(1), updatedAt = Instant.fromEpochMilliseconds(1)))

    private fun blankTag(model: NtagModel = NtagModel.NTAG213) = FakeNtag21x(model, uid = Bytes.of(0x04, nextUid++, 0xB2, 0xC3, 0xD4, 0xE5, 0xF6))
    private var nextUid = 1

    private fun tapped(tag: FakeNtag21x) = FakeTagSession.ntag(tag)

    private fun uriOn(tag: FakeNtag21x): String {
        val area = Bytes.concat((tag.model.userStart..tag.model.userEnd).map { tag.page(it) })
        val message = (Type2Tlv.unwrap(area) as Outcome.Ok).value.message
        val parsed = RecordInterpreter.interpret((DefaultNdefCodec.decode(message) as Outcome.Ok<NdefMessage>).value)
        return (parsed.first() as ParsedRecord.Uri).uri
    }

    private suspend fun TestScope.configure(vm: BatchWriteViewModel, start: String = "7", pad: String = "3", total: String = "3") {
        vm.onTemplateSelected(seedTemplate())
        vm.onStartChanged(start)
        vm.onPadChanged(pad)
        vm.onTotalChanged(total)
        runCurrent()
    }

    // ---- setup -------------------------------------------------------------------------------------------------------

    @Test
    fun theStartValueDefaultsToTheStoredCounter() = runTest {
        val store = FakeSettingsStore(AppSettings(batchNextCounter = 42))
        val vm = viewModel(store = store)
        runCurrent()
        assertEquals("42", vm.uiState.value.startText)
    }

    @Test
    fun withoutATemplateThereIsNothingToStart() = runTest {
        val vm = viewModel()
        runCurrent()
        assertFalse(vm.uiState.value.canStart)
        assertEquals(BatchBlock.NO_RECORDS, vm.uiState.value.analysis.block)
    }

    @Test
    fun aTemplateWithTheTokenAndSaneNumbersCanStart() = runTest {
        val vm = viewModel()
        configure(vm)
        assertTrue(vm.uiState.value.canStart)
        assertTrue(vm.uiState.value.usesCounter)
        assertEquals(137, vm.uiState.value.capacity)
    }

    @Test
    fun theSizeCheckLooksAtTheLongestNumberOfTheRangeBeforeAnythingIsWritten() = runTest {
        val vm = viewModel()
        vm.onTemplateSelected(seedTemplate(RecordSpec.Text("${"x".repeat(129)}{n}", "en")))
        runCurrent()
        vm.onPadChanged("1")
        vm.onStartChanged("1")
        vm.onTotalChanged("9")      // one digit: exactly fits NTAG213 (137)
        assertTrue(vm.uiState.value.canStart)

        vm.onTotalChanged("10")     // 10 needs a second digit on the last tag: one byte over
        assertFalse(vm.uiState.value.canStart)
        assertEquals(BatchBlock.OVER_CAPACITY, vm.uiState.value.analysis.block)

        vm.onTargetSelected(NtagModel.NTAG215)
        assertTrue(vm.uiState.value.canStart)
    }

    @Test
    fun garbageInTheNumberFieldsBlocksStartingInsteadOfCrashing() = runTest {
        val vm = viewModel()
        configure(vm)
        vm.onStepChanged("abc")
        assertFalse(vm.uiState.value.canStart)
        assertEquals(BatchBlock.BAD_STEP, vm.uiState.value.analysis.block)
    }

    @Test
    fun startingIsRefusedWhenNfcIsOff() = runTest {
        val vm = viewModel()
        configure(vm)
        availability.value = NfcAvailability.DISABLED
        runCurrent()
        assertFalse(vm.uiState.value.canStart)
        vm.onStart()
        runCurrent()
        assertEquals(BatchStep.SETUP, vm.uiState.value.step)
        availability.value = NfcAvailability.ENABLED
    }

    // ---- running -----------------------------------------------------------------------------------------------------

    @Test
    fun twoVerifiedWritesCarry007And008AndTheNextValueShownIs009() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        configure(vm)
        vm.onStart()
        runCurrent()
        val first = blankTag()
        val second = blankTag()

        coordinator.onTagDiscovered(tapped(first))
        advanceUntilIdle()
        coordinator.onTagDiscovered(tapped(second))
        advanceUntilIdle()

        assertEquals("https://x.co/t/007", uriOn(first))
        assertEquals("https://x.co/t/008", uriOn(second))
        val state = vm.uiState.value
        assertEquals(BatchStep.RUNNING, state.step)
        assertEquals("009", state.run!!.nextLabel)
        assertEquals(BatchStatus.Written("008"), state.status)
        assertEquals(9L, settings.current.batchNextCounter)
        assertEquals(2, history.observe().first().count { it.kind == HistoryKind.BATCH && it.outcome == HistoryOutcome.SUCCESS })
    }

    @Test
    fun aTagLostOn009KeepsTheCounterLogsTheFailureAndRetriesTheSameNumberOnTheNextTag() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        configure(vm, total = "3")
        vm.onStart()
        runCurrent()
        repeat(2) { coordinator.onTagDiscovered(tapped(blankTag())); advanceUntilIdle() }

        coordinator.onTagDiscovered(tapped(blankTag().apply { loseTagAfter(3) }))
        advanceUntilIdle()

        val failed = vm.uiState.value.status
        assertIs<BatchStatus.Failed>(failed)
        assertEquals("009", failed.label)
        assertEquals(TagError.TagLost, failed.error)
        assertEquals(9L, settings.current.batchNextCounter)
        assertEquals(HistoryOutcome.FAILED_TAGLOST, history.observe().first().first().outcome)
        assertEquals(HistoryKind.BATCH, history.observe().first().first().kind)

        val retry = blankTag()
        coordinator.onTagDiscovered(tapped(retry))
        advanceUntilIdle()

        assertEquals("https://x.co/t/009", uriOn(retry))
        val state = vm.uiState.value
        assertEquals(BatchStep.DONE, state.step)
        assertEquals(BatchSummary(ok = 3, failed = 1, remaining = 0), state.run!!.summary)
        assertEquals(10L, settings.current.batchNextCounter)
    }

    @Test
    fun theCounterSurvivesProcessDeathBecauseANewViewModelStartsFromTheStoredValue() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        configure(vm, total = "10")
        vm.onStart()
        runCurrent()
        repeat(2) { coordinator.onTagDiscovered(tapped(blankTag())); advanceUntilIdle() }

        val recreated = viewModel(coordinator())   // the process died: new ViewModel, same stored settings
        runCurrent()

        assertEquals("9", recreated.uiState.value.startText)
    }

    @Test
    fun presentingTheSameTagAgainAsksFirstAndDecliningWritesNothing() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        configure(vm, total = "5")
        vm.onStart()
        runCurrent()
        val tag = blankTag()
        coordinator.onTagDiscovered(tapped(tag))
        advanceUntilIdle()
        val writesAfterFirst = tag.writeCount

        coordinator.onTagDiscovered(tapped(tag))
        runCurrent()
        assertEquals(BatchPrompt.SameTag, vm.uiState.value.prompt)
        vm.onPromptDecision(false)
        advanceUntilIdle()

        assertEquals(writesAfterFirst, tag.writeCount)
        assertNull(vm.uiState.value.prompt)
        assertEquals("008", vm.uiState.value.run!!.nextLabel)   // nothing advanced
        assertEquals(1, history.observe().first().size, "a declined rewrite is not a write attempt")
    }

    @Test
    fun confirmingTheSameTagRewritesItWithTheNextNumber() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        configure(vm, total = "5")
        vm.onStart()
        runCurrent()
        val tag = blankTag()
        coordinator.onTagDiscovered(tapped(tag))
        advanceUntilIdle()

        coordinator.onTagDiscovered(tapped(tag))
        runCurrent()
        vm.onPromptDecision(true)
        advanceUntilIdle()

        assertEquals("https://x.co/t/008", uriOn(tag))
        assertEquals("009", vm.uiState.value.run!!.nextLabel)
    }

    @Test
    fun skippingAFailedNumberMovesOnAndStoresTheNewCounter() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        configure(vm, total = "3")
        vm.onStart()
        runCurrent()
        coordinator.onTagDiscovered(tapped(blankTag().apply { loseTagAfter(3) }))
        advanceUntilIdle()
        assertIs<BatchStatus.Failed>(vm.uiState.value.status)

        vm.onSkip()
        runCurrent()

        assertEquals("008", vm.uiState.value.run!!.nextLabel)
        assertEquals(8L, settings.current.batchNextCounter)
        assertEquals(BatchStatus.Waiting, vm.uiState.value.status)
    }

    @Test
    fun stoppingShowsTheSummaryAndStopsWritingTags() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        configure(vm, total = "10")
        vm.onStart()
        runCurrent()
        repeat(4) { coordinator.onTagDiscovered(tapped(blankTag())); advanceUntilIdle() }

        vm.onStop()
        val late = blankTag()
        coordinator.onTagDiscovered(tapped(late))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(BatchStep.DONE, state.step)
        assertEquals(BatchSummary(ok = 4, failed = 0, remaining = 6), state.run!!.summary)
        assertEquals(0, late.writeCount)
        assertEquals(11L, settings.current.batchNextCounter)
    }

    @Test
    fun leavingTheScreenDisarmsAndComingBackReArmsTheSameBatch() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        configure(vm, total = "5")
        vm.onStart()
        runCurrent()
        vm.onHidden()
        val whileAway = blankTag()
        coordinator.onTagDiscovered(tapped(whileAway))
        advanceUntilIdle()
        assertEquals(0, whileAway.writeCount)

        vm.onVisible()
        val back = blankTag()
        coordinator.onTagDiscovered(tapped(back))
        advanceUntilIdle()

        assertEquals("https://x.co/t/007", uriOn(back))
    }

    @Test
    fun startingOverReturnsToSetupAndKeepsTheStoredCounter() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        configure(vm, total = "2")
        vm.onStart()
        runCurrent()
        repeat(2) { coordinator.onTagDiscovered(tapped(blankTag())); advanceUntilIdle() }
        assertEquals(BatchStep.DONE, vm.uiState.value.step)

        vm.onNewBatch()
        runCurrent()

        assertEquals(BatchStep.SETUP, vm.uiState.value.step)
        assertNull(vm.uiState.value.run)
        assertEquals("9", vm.uiState.value.startText)
    }
}
