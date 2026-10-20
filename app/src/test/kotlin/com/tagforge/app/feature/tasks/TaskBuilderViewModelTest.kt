package com.tagforge.app.feature.tasks

import app.cash.turbine.test
import com.tagforge.app.feature.write.OverwritePrompt
import com.tagforge.app.feature.write.WriteSheet
import com.tagforge.app.feature.write.WriteTarget
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeSettingsStore
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.FixedClock
import com.tagforge.domain.fakes.InMemoryHistoryRepository
import com.tagforge.domain.fakes.InMemoryTaskRepository
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
import com.tagforge.domain.task.BlockReason
import com.tagforge.domain.task.JoinAuth
import com.tagforge.domain.task.RingerMode
import com.tagforge.domain.task.Task
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.task.TaskBlock
import com.tagforge.domain.task.TaskCodec
import com.tagforge.domain.task.TaskFingerprint
import com.tagforge.domain.task.TaskMessageBuilder
import com.tagforge.domain.task.TaskPayload
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** TK-01, TK-03, TK-04, TK-10: build and edit with a live byte meter, save, and write-to-tag with verify and overwrite confirmation. */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskBuilderViewModelTest {

    private val availability = MutableStateFlow(NfcAvailability.ENABLED)
    private val settings = FakeSettingsStore()
    private val history = InMemoryHistoryRepository()
    private val tasks = InMemoryTaskRepository()
    private val clock = FixedClock(Instant.fromEpochMilliseconds(5_000))

    private val ringer = TaskAction.SetRinger(RingerMode.VIBRATE)
    private val url = TaskAction.OpenUrl("https://example.com")

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.coordinator() = ScanCoordinator(this, StandardTestDispatcher(testScheduler))

    private fun TestScope.viewModel(coordinator: ScanCoordinator = coordinator(), taskId: Long? = null) = TaskBuilderViewModel(
        coordinator = coordinator,
        settings = settings,
        recordHistory = RecordHistoryUseCase(history, settings, clock),
        tasks = tasks,
        clock = clock,
        appPackage = "com.tagforge.app",
        availability = availability,
        taskId = taskId,
    )

    private fun TaskBuilderViewModel.add(kind: ActionKind, action: TaskAction) {
        onAddAction()
        onKindSelected(kind)
        onActionChanged(action)
        onConfirmAction()
    }

    private fun blankTag(model: NtagModel = NtagModel.NTAG215) = FakeNtag21x(model)
    private fun session(tag: FakeNtag21x) = FakeTagSession.ntag(tag)
    private fun seed(tag: FakeNtag21x, message: NdefMessage) = kotlinx.coroutines.runBlocking { session(tag).writeNdef(message) }

    private fun userContent(tag: FakeNtag21x): NdefMessage {
        val area = Bytes.concat((tag.model.userStart..tag.model.userEnd).map { tag.page(it) })
        val unwrapped = (Type2Tlv.unwrap(area) as Outcome.Ok).value
        return (DefaultNdefCodec.decode(unwrapped.message) as Outcome.Ok).value
    }

    // ---- building (TK-01) ---------------------------------------------------------------------------------------

    @Test
    fun startsEmptyOnAnNtag213MeterAndCannotBeSavedOrWritten() = runTest {
        val state = viewModel().uiState.value

        assertEquals(emptyList(), state.actions)
        assertEquals(TaskBlock.NO_ACTIONS, state.analysis.block)
        assertEquals(137, state.capacity)
        assertFalse(state.canWrite)
        assertFalse(state.canSave)
    }

    @Test
    fun pickingAKindOpensTheFormWithASensibleDefaultAndConfirmingAddsIt() = runTest {
        val vm = viewModel()

        vm.onAddAction()
        assertTrue(vm.uiState.value.picking)
        vm.onKindSelected(ActionKind.RINGER)

        assertEquals(EditingAction(index = null, action = ringer), vm.uiState.value.editing)
        assertFalse(vm.uiState.value.picking)

        vm.onConfirmAction()

        assertEquals(listOf<TaskAction>(ringer), vm.uiState.value.actions)
        assertNull(vm.uiState.value.editing)
    }

    @Test
    fun addAndReorderListsTheActionsInTheNewOrderWithALiveMeter() = runTest {
        val vm = viewModel()
        vm.add(ActionKind.RINGER, ringer)
        vm.add(ActionKind.OPEN_URL, url)

        vm.onMoveAction(1, -1)

        val state = vm.uiState.value
        assertEquals(listOf(url, ringer), state.actions)
        val payload = (TaskCodec.encode(TaskPayload(null, listOf(url, ringer))) as Outcome.Ok).value.size
        assertEquals(19 + payload + 34, state.analysis.budget.used)
        assertEquals(137, state.analysis.budget.capacity)
        assertEquals(listOf(19 + payload, 34), state.analysis.budget.perRecord)
    }

    @Test
    fun theSeventeenthActionCannotBeAdded() = runTest {
        val vm = viewModel()
        repeat(16) { vm.add(ActionKind.FLASHLIGHT, TaskAction.Flashlight(it % 2 == 0)) }

        assertTrue(vm.uiState.value.isFull)
        vm.onAddAction()

        assertFalse(vm.uiState.value.picking, "the picker does not open on a full task")
        assertEquals(16, vm.uiState.value.actions.size)
    }

    @Test
    fun aBlockedActionCannotBeAddedAndTheFormExplainsWhy() = runTest {
        val vm = viewModel()
        vm.onAddAction()
        vm.onKindSelected(ActionKind.OPEN_URL)
        vm.onActionChanged(TaskAction.OpenUrl("javascript:alert(1)"))

        vm.onConfirmAction()

        val editing = assertNotNull(vm.uiState.value.editing)
        assertEquals(emptyList(), vm.uiState.value.actions)
        assertTrue(editing.showAllErrors)
        assertEquals(BlockReason.DISALLOWED_SCHEME, editing.blockReason)
    }

    @Test
    fun editingAnActionReplacesItInPlace() = runTest {
        val vm = viewModel()
        vm.add(ActionKind.RINGER, ringer)
        vm.add(ActionKind.OPEN_URL, url)

        vm.onEditAction(0)
        assertEquals(EditingAction(0, ringer), vm.uiState.value.editing)
        vm.onActionChanged(TaskAction.SetRinger(RingerMode.SILENT))
        vm.onConfirmAction()

        assertEquals(listOf(TaskAction.SetRinger(RingerMode.SILENT), url), vm.uiState.value.actions)
    }

    @Test
    fun cancellingAnEditDiscardsIt() = runTest {
        val vm = viewModel()
        vm.add(ActionKind.RINGER, ringer)
        vm.onEditAction(0)
        vm.onActionChanged(TaskAction.SetRinger(RingerMode.NORMAL))

        vm.onCancelEditing()

        assertNull(vm.uiState.value.editing)
        assertEquals(listOf<TaskAction>(ringer), vm.uiState.value.actions)
    }

    @Test
    fun removingAnActionCanBeUndone() = runTest {
        val vm = viewModel()
        vm.add(ActionKind.RINGER, ringer)
        vm.add(ActionKind.OPEN_URL, url)

        vm.events.test {
            vm.onRemoveAction(0)
            assertEquals(TaskBuilderEvent.ActionRemoved, awaitItem())
        }
        assertEquals(listOf<TaskAction>(url), vm.uiState.value.actions)

        vm.onUndoRemove()

        assertEquals(listOf(ringer, url), vm.uiState.value.actions)
    }

    // ---- the byte meter and the target (TK-03) -----------------------------------------------------------------------

    @Test
    fun anOversizedTaskTurnsTheMeterRedDisablesWriteAndRecommendsABiggerChip() = runTest {
        val vm = viewModel()
        vm.add(ActionKind.SPEAK, TaskAction.Speak("a".repeat(100)))
        vm.add(ActionKind.SPEAK, TaskAction.Speak("b".repeat(100)))

        val state = vm.uiState.value
        assertEquals(TaskBlock.OVER_CAPACITY, state.analysis.block)
        assertFalse(state.canWrite)
        assertEquals(NtagModel.NTAG215, state.analysis.suggestedChip)
        assertTrue(state.analysis.budget.overBy > 0)

        vm.onTargetSelected(WriteTarget.Chip(NtagModel.NTAG215))

        assertEquals(492, vm.uiState.value.capacity)
        assertNull(vm.uiState.value.analysis.block)
        assertTrue(vm.uiState.value.canWrite)
    }

    @Test
    fun theChosenTargetIsRememberedForNextTime() = runTest {
        val vm = viewModel()

        vm.onTargetSelected(WriteTarget.Chip(NtagModel.NTAG216))
        advanceUntilIdle()

        assertEquals(NtagModel.NTAG216, settings.settings.first().lastTargetModel)
    }

    // ---- saving (TK-10) ------------------------------------------------------------------------------------------------

    @Test
    fun savingNeedsANameAndOnceNamedStoresTheTask() = runTest {
        val vm = viewModel()
        vm.add(ActionKind.RINGER, ringer)
        vm.add(ActionKind.OPEN_URL, url)

        vm.onSave()
        assertTrue(vm.uiState.value.nameError, "a name is required")
        assertEquals(emptyList(), tasks.observeAll().first())

        vm.onNameChanged("  Night mode ")
        assertFalse(vm.uiState.value.nameError)
        vm.events.test {
            vm.onSave()
            assertEquals(TaskBuilderEvent.Saved("Night mode"), awaitItem())
        }

        val saved = tasks.observeAll().first().single()
        assertEquals("Night mode", saved.name)
        assertEquals(listOf(ringer, url), saved.actions)
        assertEquals(Instant.fromEpochMilliseconds(5_000), saved.createdAt)
        assertEquals(saved.id, vm.uiState.value.taskId)
    }

    @Test
    fun aSavedTaskIsRememberedAsMineByItsFingerprint() = runTest {
        val vm = viewModel()
        vm.add(ActionKind.RINGER, ringer)
        vm.onNameChanged("Night mode")

        vm.onSave()

        val expected = TaskFingerprint.of("Night mode", listOf(ringer))
        assertEquals(setOf(expected), settings.current.trustedTaskFingerprints)
    }

    @Test
    fun savingAgainUpdatesTheSameTaskInsteadOfDuplicatingIt() = runTest {
        val vm = viewModel()
        vm.onNameChanged("Night")
        vm.add(ActionKind.RINGER, ringer)
        vm.onSave()

        clock.advanceMillis(1_000)
        vm.add(ActionKind.OPEN_URL, url)
        vm.onSave()

        val saved = tasks.observeAll().first().single()
        assertEquals(listOf(ringer, url), saved.actions)
        assertEquals(Instant.fromEpochMilliseconds(5_000), saved.createdAt)
        assertEquals(Instant.fromEpochMilliseconds(6_000), saved.updatedAt)
    }

    @Test
    fun aNameAnotherTaskAlreadyUsesGetsANumberedSuffix() = runTest {
        tasks.upsert(Task(name = "Night", actions = listOf(ringer), createdAt = clock.now(), updatedAt = clock.now()))
        val vm = viewModel()
        vm.onNameChanged("night")
        vm.add(ActionKind.RINGER, ringer)

        vm.events.test {
            vm.onSave()
            assertEquals(TaskBuilderEvent.Saved("night (2)"), awaitItem())
        }
    }

    @Test
    fun aTaskWithABlockedActionCannotBeSaved() = runTest {
        val vm = viewModel(taskId = tasks.upsert(Task(name = "Old", actions = listOf(TaskAction.OpenUrl("intent://x")), createdAt = clock.now(), updatedAt = clock.now())))
        runCurrent()

        assertFalse(vm.uiState.value.canSave)
        vm.onSave()
        assertEquals(TaskAction.OpenUrl("intent://x"), tasks.observeAll().first().single().actions.single(), "nothing was rewritten")
    }

    @Test
    fun openingASavedTaskLoadsItsNameAndActionsAndSavingKeepsItsId() = runTest {
        val id = tasks.upsert(Task(name = "Night", actions = listOf(ringer, url), createdAt = Instant.fromEpochMilliseconds(100), updatedAt = Instant.fromEpochMilliseconds(100)))

        val vm = viewModel(taskId = id)
        runCurrent()

        val state = vm.uiState.value
        assertEquals("Night", state.name)
        assertEquals(listOf(ringer, url), state.actions)
        assertEquals(id, state.taskId)

        vm.onNameChanged("Night v2")
        vm.onSave()

        val saved = tasks.observeAll().first().single()
        assertEquals(id, saved.id)
        assertEquals("Night v2", saved.name)
        assertEquals(Instant.fromEpochMilliseconds(100), saved.createdAt)
        assertEquals(Instant.fromEpochMilliseconds(5_000), saved.updatedAt)
    }

    @Test
    fun openingAMissingTaskStartsABlankBuilder() = runTest {
        val vm = viewModel(taskId = 404)
        runCurrent()

        assertEquals(emptyList(), vm.uiState.value.actions)
        assertNull(vm.uiState.value.taskId)
        assertFalse(vm.uiState.value.loading)
    }

    // ---- write to tag (TK-04) ------------------------------------------------------------------------------------------

    @Test
    fun aVerifiedWriteReadsBackAsAnEqualTask() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onNameChanged("Night")
        vm.add(ActionKind.RINGER, ringer)
        vm.add(ActionKind.OPEN_URL, url)
        val tag = blankTag()

        vm.onWrite()
        assertIs<WriteSheet.Waiting>(vm.uiState.value.sheet)
        coordinator.onTagDiscovered(session(tag))
        advanceUntilIdle()

        val sheet = assertIs<WriteSheet.Success>(vm.uiState.value.sheet)
        assertEquals(2, sheet.report.written.records.size)
        val back = (TaskMessageBuilder.extract(userContent(tag)) as Outcome.Ok).value
        assertEquals(TaskPayload("Night", listOf(ringer, url)), back)
        assertEquals(NdefRecord.aar("com.tagforge.app"), userContent(tag).records.last(), "the AAR is last")
    }

    @Test
    fun aVerifiedWriteRemembersTheTaskAsMineAndAFailedOneDoesNot() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onNameChanged("Night")
        vm.add(ActionKind.RINGER, ringer)

        vm.onWrite()
        coordinator.onTagDiscovered(session(blankTag().apply { loseTagAfter(3) }))
        advanceUntilIdle()
        assertEquals(emptySet(), settings.current.trustedTaskFingerprints, "a failed write trusts nothing")

        vm.onRetry()
        coordinator.onTagDiscovered(session(blankTag()))
        advanceUntilIdle()
        assertEquals(setOf(TaskFingerprint.of("Night", listOf(ringer))), settings.current.trustedTaskFingerprints)
    }

    @Test
    fun theWriteIsLoggedAndTheHistoryNeverHoldsAWifiKey() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onNameChanged("Guest")
        vm.add(ActionKind.WIFI_JOIN, TaskAction.WifiJoin("Home", "pass1234", JoinAuth.WPA2))

        vm.onWrite()
        coordinator.onTagDiscovered(session(blankTag()))
        advanceUntilIdle()

        val entry = history.observe().first().single()
        assertEquals(HistoryKind.WRITE, entry.kind)
        assertEquals(HistoryOutcome.SUCCESS, entry.outcome)
        assertEquals("Guest · com.tagforge.app", entry.summary)
        val stored = entry.ndef!!.toByteArray().toString(Charsets.ISO_8859_1)
        assertFalse("pass1234" in stored, "history must not keep the Wi-Fi key")
    }

    @Test
    fun existingContentAsksFirstAndDecliningLeavesTheTagUntouched() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.add(ActionKind.RINGER, ringer)
        val tag = blankTag()
        val old = NdefMessage(listOf(NdefRecord.text("keep me", "en")))
        seed(tag, old)
        val writesBefore = tag.writeCount

        vm.onWrite()
        coordinator.onTagDiscovered(session(tag))
        runCurrent()

        assertEquals(OverwritePrompt("keep me"), vm.uiState.value.overwrite)
        assertEquals(writesBefore, tag.writeCount, "nothing is written while the question is open")

        vm.onOverwriteDecision(false)
        advanceUntilIdle()

        assertEquals(writesBefore, tag.writeCount)
        assertEquals(old, userContent(tag))
        assertNull(vm.uiState.value.sheet)
        assertEquals(0, history.observe().first().size, "a declined overwrite is not a write attempt")
    }

    @Test
    fun acceptingTheOverwriteWritesTheTask() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.add(ActionKind.RINGER, ringer)
        val tag = blankTag()
        seed(tag, NdefMessage(listOf(NdefRecord.text("old", "en"))))

        vm.onWrite()
        coordinator.onTagDiscovered(session(tag))
        runCurrent()
        vm.onOverwriteDecision(true)
        advanceUntilIdle()

        assertIs<WriteSheet.Success>(vm.uiState.value.sheet)
        assertEquals(TaskPayload(null, listOf(ringer)), (TaskMessageBuilder.extract(userContent(tag)) as Outcome.Ok).value)
    }

    @Test
    fun aTagPulledAwayMidWriteIsAFailureLoggedAsTagLost() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.add(ActionKind.RINGER, ringer)

        vm.events.test {
            vm.onWrite()
            coordinator.onTagDiscovered(session(blankTag().apply { loseTagAfter(3) }))
            advanceUntilIdle()
            assertEquals(TaskBuilderEvent.TagDetected, awaitItem())
            assertEquals(TaskBuilderEvent.WriteFailed, awaitItem())
        }

        val sheet = assertIs<WriteSheet.Failure>(vm.uiState.value.sheet)
        assertEquals(TagError.TagLost, sheet.error)
        assertTrue(sheet.canRetry)
        assertEquals(HistoryOutcome.FAILED_TAGLOST, history.observe().first().single().outcome)
    }

    @Test
    fun aTooSmallTagReportsItsRealCapacityAndOffersNoRetry() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onTargetSelected(WriteTarget.Chip(NtagModel.NTAG215))
        vm.add(ActionKind.SPEAK, TaskAction.Speak("a".repeat(100)))
        vm.add(ActionKind.SPEAK, TaskAction.Speak("b".repeat(100)))

        vm.onWrite()
        coordinator.onTagDiscovered(session(blankTag(NtagModel.NTAG213)))
        advanceUntilIdle()

        val sheet = assertIs<WriteSheet.Failure>(vm.uiState.value.sheet)
        assertEquals(TagError.CapacityExceeded(19 + 206 + 34, 137), sheet.error)
        assertFalse(sheet.canRetry)
        assertEquals(WriteTarget.LastTag(137), vm.uiState.value.target)
    }

    @Test
    fun writingIsRefusedWhileTheTaskIsInvalidTooBigOrNfcIsOff() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)

        vm.onWrite()
        assertNull(vm.uiState.value.sheet, "empty task")

        vm.add(ActionKind.RINGER, ringer)
        availability.value = NfcAvailability.DISABLED
        vm.onWrite()
        assertNull(vm.uiState.value.sheet, "NFC off")
        availability.value = NfcAvailability.ENABLED
        assertTrue(vm.uiState.value.canWrite)
    }

    @Test
    fun leavingTheScreenBeforeATagArrivesDisarmsTheWrite() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.add(ActionKind.RINGER, ringer)
        val tag = blankTag()

        vm.onWrite()
        vm.onHidden()
        coordinator.onTagDiscovered(session(tag))
        advanceUntilIdle()

        assertEquals(0, tag.writeCount)
        assertNull(vm.uiState.value.sheet)
    }

    @Test
    fun dismissingTheSheetWhileTheQuestionIsOpenCountsAsDeclining() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.add(ActionKind.RINGER, ringer)
        val tag = blankTag()
        seed(tag, NdefMessage(listOf(NdefRecord.text("keep", "en"))))
        val writesBefore = tag.writeCount

        vm.onWrite()
        coordinator.onTagDiscovered(session(tag))
        runCurrent()
        vm.onSheetDismissed()
        advanceUntilIdle()

        assertEquals(writesBefore, tag.writeCount)
        assertNull(vm.uiState.value.sheet)
        assertNull(vm.uiState.value.overwrite)
    }
}
