package com.tagforge.app.feature.write

import app.cash.turbine.test
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.common.Outcome
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
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.ParsedRecord
import com.tagforge.domain.ndef.spec.FieldError
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.SpecError
import com.tagforge.domain.ndef.spec.SpecField
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.template.Template
import com.tagforge.domain.write.WriteBlock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

/** TW-01..TW-06, HT-02, G4/G5: the whole write wizard as state transitions, with FakeNtag21x behind the coordinator. */
@OptIn(ExperimentalCoroutinesApi::class)
class WriteViewModelTest {

    private val availability = MutableStateFlow(NfcAvailability.ENABLED)
    private val settings = FakeSettingsStore()
    private val history = InMemoryHistoryRepository()
    private val templates = InMemoryTemplateRepository()
    private val clock = FixedClock(Instant.fromEpochMilliseconds(5_000))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.coordinator() = ScanCoordinator(this, StandardTestDispatcher(testScheduler))

    private fun TestScope.viewModel(
        coordinator: ScanCoordinator = coordinator(),
        templateId: Long? = null,
        initialRecords: List<RecordSpec> = emptyList(),
    ) = WriteViewModel(
        coordinator = coordinator,
        settings = settings,
        recordHistory = RecordHistoryUseCase(history, settings, clock),
        templates = templates,
        clock = clock,
        appPackage = "com.tagforge.app",
        availability = availability,
        initialTemplateId = templateId,
        initialRecords = initialRecords,
    )

    private fun WriteViewModel.addRecord(spec: RecordSpec) {
        onAddRecord()
        onTypeSelected(spec.kind())
        onDraftChanged(spec)
        onContinue()
    }

    private val uri = RecordSpec.Uri("https://example.com")   // 16 bytes
    private fun blankTag(model: NtagModel = NtagModel.NTAG213) = FakeNtag21x(model)
    private fun session(tag: FakeNtag21x) = FakeTagSession.ntag(tag)

    private fun userContent(tag: FakeNtag21x): NdefMessage {
        val area = Bytes.concat((tag.model.userStart..tag.model.userEnd).map { tag.page(it) })
        val unwrapped = (com.tagforge.domain.ndef.Type2Tlv.unwrap(area) as Outcome.Ok).value
        return (DefaultNdefCodec.decode(unwrapped.message) as Outcome.Ok).value
    }

    // ---- wizard: choosing, filling, validating ---------------------------------------------------------------

    @Test
    fun startsOnTheTypePickerWithNothingToWrite() = runTest {
        val state = viewModel().uiState.value

        assertEquals(WriteStep.TYPE, state.step)
        assertEquals(emptyList(), state.records)
        assertEquals(WriteBlock.NO_RECORDS, state.analysis.block)
        assertFalse(state.canWrite)
        assertEquals(WriteTarget.Chip(NtagModel.NTAG213), state.target)
        assertEquals(137, state.capacity)
    }

    @Test
    fun pickingATypeOpensAnEmptyFormOfThatType() = runTest {
        val vm = viewModel()
        vm.onTypeSelected(RecordKind.WIFI)

        val state = vm.uiState.value
        assertEquals(WriteStep.FORM, state.step)
        assertEquals(RecordSpec.Wifi(), state.editing?.spec)
        assertNull(state.editing?.index, "a new record, not an edit")
    }

    @Test
    fun errorsStayHiddenUntilTheFieldHasBeenTouched() = runTest {
        val vm = viewModel()
        vm.onTypeSelected(RecordKind.EMAIL)

        assertEquals(emptyList(), vm.uiState.value.editing?.visibleErrors)

        vm.onFieldTouched(SpecField.TO)
        assertEquals(listOf(FieldError(SpecField.TO, SpecError.REQUIRED)), vm.uiState.value.editing?.visibleErrors)
    }

    @Test
    fun wifiWithATooShortPasswordShowsTheErrorBesideTheFieldOnceItLosesFocus() = runTest {
        val vm = viewModel()
        vm.onTypeSelected(RecordKind.WIFI)
        vm.onDraftChanged(RecordSpec.Wifi(ssid = "Home", password = "123"))
        vm.onFieldTouched(SpecField.PASSWORD)

        val editing = vm.uiState.value.editing
        assertEquals(listOf(FieldError(SpecField.PASSWORD, SpecError.PASSWORD_LENGTH)), editing?.visibleErrors)
    }

    @Test
    fun continuingWithAnIncompleteFormRevealsEveryErrorAndStaysPut() = runTest {
        val vm = viewModel()
        vm.onTypeSelected(RecordKind.GEO)
        vm.onContinue()

        val state = vm.uiState.value
        assertEquals(WriteStep.FORM, state.step)
        assertEquals(emptyList(), state.records)
        assertEquals(
            listOf(FieldError(SpecField.LATITUDE, SpecError.REQUIRED), FieldError(SpecField.LONGITUDE, SpecError.REQUIRED)),
            state.editing?.visibleErrors,
        )
    }

    @Test
    fun continuingWithAValidFormAddsTheRecordAndMovesToReview() = runTest {
        val vm = viewModel()
        vm.onTypeSelected(RecordKind.URL)
        vm.onDraftChanged(uri)
        vm.onContinue()

        val state = vm.uiState.value
        assertEquals(WriteStep.REVIEW, state.step)
        assertEquals(listOf<RecordSpec>(uri), state.records)
        assertNull(state.editing)
        assertTrue(state.canWrite)
    }

    // ---- live preview and budget -------------------------------------------------------------------------------

    @Test
    fun theLivePreviewAppearsOnlyOnceTheDraftEncodes() = runTest {
        val vm = viewModel()
        vm.onTypeSelected(RecordKind.URL)
        assertNull(vm.uiState.value.previewHex)

        vm.onDraftChanged(uri)
        val state = vm.uiState.value
        assertEquals("D1 01 0C 55 04 65 78 61 6D 70 6C 65 2E 63 6F 6D", state.previewHex)
        assertIs<ParsedRecord.Uri>(state.previewParsed)
        assertEquals("https://example.com", (state.previewParsed as ParsedRecord.Uri).uri)

        vm.onDraftChanged(RecordSpec.Uri("example.com"))
        assertNull(vm.uiState.value.previewHex)
    }

    @Test
    fun theMeterCountsTheRecordBeingTypedAlongsideTheSavedOnes() = runTest {
        val vm = viewModel()
        vm.addRecord(uri)
        assertEquals(16, vm.uiState.value.analysis.budget.used)

        vm.onAddRecord()
        vm.onTypeSelected(RecordKind.TEXT)
        vm.onDraftChanged(RecordSpec.Text("hi", "en"))
        assertEquals(16 + 9, vm.uiState.value.analysis.budget.used)
        assertEquals(137, vm.uiState.value.analysis.budget.capacity)
    }

    @Test
    fun aMessageOneByteOverNtag213IsBlockedAndNtag215IsSuggested() = runTest {
        val vm = viewModel()
        vm.addRecord(RecordSpec.Text("a".repeat(131)))   // 138 bytes

        val state = vm.uiState.value
        assertEquals(WriteBlock.OVER_CAPACITY, state.analysis.block)
        assertEquals(1, state.analysis.budget.overBy)
        assertFalse(state.canWrite)
        assertEquals(NtagModel.NTAG215, state.suggestedChip)
    }

    @Test
    fun choosingALargerTargetUnblocksWritingAndRemembersTheChoice() = runTest {
        val vm = viewModel()
        vm.addRecord(RecordSpec.Text("a".repeat(131)))

        vm.onTargetSelected(WriteTarget.Chip(NtagModel.NTAG215))
        runCurrent()

        val state = vm.uiState.value
        assertEquals(492, state.capacity)
        assertNull(state.analysis.block)
        assertTrue(state.canWrite)
        assertEquals(NtagModel.NTAG215, settings.current.lastTargetModel)
    }

    @Test
    fun theRememberedTargetChipIsRestoredNextTime() = runTest {
        settings.update { it.copy(lastTargetModel = NtagModel.NTAG216) }
        runCurrent()

        val state = viewModel().uiState.value

        assertEquals(WriteTarget.Chip(NtagModel.NTAG216), state.target)
        assertEquals(868, state.capacity)
    }

    @Test
    fun theAarSwitchAddsThirtyFourBytesAndAnAarRecordLast() = runTest {
        val vm = viewModel()
        vm.addRecord(uri)

        vm.onIncludeAarChanged(true)

        val state = vm.uiState.value
        assertEquals(16 + 34, state.analysis.budget.used)
        assertEquals(NdefRecord.aar("com.tagforge.app"), state.analysis.message?.records?.last())
    }

    // ---- editing the list ---------------------------------------------------------------------------------------

    @Test
    fun editingARecordReplacesItInPlaceAndKeepsItsPosition() = runTest {
        val vm = viewModel()
        vm.addRecord(RecordSpec.Text("one"))
        vm.addRecord(uri)

        vm.onEditRecord(0)
        assertEquals(WriteStep.FORM, vm.uiState.value.step)
        assertEquals(RecordSpec.Text("one"), vm.uiState.value.editing?.spec)
        vm.onDraftChanged(RecordSpec.Text("uno", "es"))
        vm.onContinue()

        assertEquals(listOf<RecordSpec>(RecordSpec.Text("uno", "es"), uri), vm.uiState.value.records)
        assertEquals(WriteStep.REVIEW, vm.uiState.value.step)
    }

    @Test
    fun editingDoesNotDoubleCountTheRecordInTheMeter() = runTest {
        val vm = viewModel()
        vm.addRecord(uri)
        val before = vm.uiState.value.analysis.budget.used

        vm.onEditRecord(0)

        assertEquals(before, vm.uiState.value.analysis.budget.used)
    }

    @Test
    fun removingARecordCanBeUndoneIntoTheSamePosition() = runTest {
        val vm = viewModel()
        vm.addRecord(RecordSpec.Text("a"))
        vm.addRecord(uri)
        vm.addRecord(RecordSpec.Phone("+57300"))

        vm.onRemoveRecord(1)
        assertEquals(listOf<RecordSpec>(RecordSpec.Text("a"), RecordSpec.Phone("+57300")), vm.uiState.value.records)
        assertTrue(vm.uiState.value.canUndoRemove)

        vm.onUndoRemove()
        assertEquals(listOf(RecordSpec.Text("a"), uri, RecordSpec.Phone("+57300")), vm.uiState.value.records)
        assertFalse(vm.uiState.value.canUndoRemove)
    }

    @Test
    fun removingARecordAnnouncesItSoTheScreenCanOfferUndo() = runTest {
        val vm = viewModel()
        vm.addRecord(uri)
        vm.addRecord(RecordSpec.Text("a"))

        vm.events.test {
            vm.onRemoveRecord(0)
            assertEquals(WriteEvent.RecordRemoved, awaitItem())
        }
    }

    @Test
    fun recordsCanBeReordered() = runTest {
        val vm = viewModel()
        vm.addRecord(RecordSpec.Text("a"))
        vm.addRecord(uri)

        vm.onMoveRecord(1, -1)

        assertEquals(listOf(uri, RecordSpec.Text("a")), vm.uiState.value.records)
    }

    @Test
    fun anInvalidSavedRecordIsFlaggedInTheReviewAndBlocksWriting() = runTest {
        val id = templates.upsert(Template(name = "Broken", records = listOf(RecordSpec.Uri("")), createdAt = clock.now(), updatedAt = clock.now()))
        val vm = viewModel(templateId = id)
        runCurrent()

        val state = vm.uiState.value
        assertEquals(WriteBlock.INVALID_RECORD, state.analysis.block)
        assertEquals(setOf(0), state.analysis.invalid.keys)
        assertFalse(state.canWrite)
    }

    // ---- back navigation -------------------------------------------------------------------------------------------

    @Test
    fun backFromTheFormGoesToTheTypePickerWhenThereAreNoRecordsYet() = runTest {
        val vm = viewModel()
        vm.onTypeSelected(RecordKind.TEXT)

        assertTrue(vm.onBack())
        assertEquals(WriteStep.TYPE, vm.uiState.value.step)
        assertNull(vm.uiState.value.editing)
    }

    @Test
    fun backFromTheFormOrPickerReturnsToReviewWhenThereAreRecords() = runTest {
        val vm = viewModel()
        vm.addRecord(uri)

        vm.onAddRecord()
        assertEquals(WriteStep.TYPE, vm.uiState.value.step)
        assertTrue(vm.onBack())
        assertEquals(WriteStep.REVIEW, vm.uiState.value.step)

        vm.onEditRecord(0)
        assertTrue(vm.onBack())
        assertEquals(WriteStep.REVIEW, vm.uiState.value.step)
        assertEquals(listOf<RecordSpec>(uri), vm.uiState.value.records, "backing out of an edit discards the edit")
    }

    @Test
    fun backFromTheReviewIsLeftToTheNavigationHost() = runTest {
        val vm = viewModel()
        vm.addRecord(uri)
        assertFalse(vm.onBack())
        assertEquals(WriteStep.REVIEW, vm.uiState.value.step)
    }

    // ---- templates (HT-02) --------------------------------------------------------------------------------------------

    @Test
    fun startingFromATemplatePrefillsTheIdenticalRecordsAtTheReview() = runTest {
        val records = listOf(RecordSpec.Wifi("Home", "pass1234"), RecordSpec.Text("hola", "es"))
        val id = templates.upsert(Template(name = "WiFi casa", records = records, createdAt = clock.now(), updatedAt = clock.now()))

        val vm = viewModel(templateId = id)
        runCurrent()

        assertEquals(records, vm.uiState.value.records)
        assertEquals(WriteStep.REVIEW, vm.uiState.value.step)
    }

    // ---- QR hand-off (PR-02) ------------------------------------------------------------------------------------------

    @Test
    fun startingFromAQrPrefillsTheDecodedRecordAtTheReview() = runTest {
        val wifi = RecordSpec.Wifi("Home", "pass1234")
        val vm = viewModel(initialRecords = listOf(wifi))
        runCurrent()

        assertEquals(listOf<RecordSpec>(wifi), vm.uiState.value.records)
        assertEquals(WriteStep.REVIEW, vm.uiState.value.step)
    }

    @Test
    fun aSingleValidRecordOffersAQrPayloadButSeveralOrInvalidOnesDoNot() = runTest {
        val vm = viewModel()
        vm.addRecord(uri)
        assertEquals("https://example.com", vm.uiState.value.qrPayload)

        vm.addRecord(RecordSpec.Text("second"))
        assertEquals(null, vm.uiState.value.qrPayload)

        val invalid = viewModel(initialRecords = listOf(RecordSpec.Uri("")))
        runCurrent()
        assertEquals(null, invalid.uiState.value.qrPayload)
    }

    @Test
    fun savingATemplateStoresTheCurrentRecordsAndAnnouncesTheName() = runTest {
        val vm = viewModel()
        vm.addRecord(uri)

        vm.events.test {
            vm.onSaveTemplate("  Mi enlace ")
            assertEquals(WriteEvent.TemplateSaved("Mi enlace"), awaitItem())
        }

        val saved = templates.observeAll().first().single()
        assertEquals("Mi enlace", saved.name)
        assertEquals(listOf<RecordSpec>(uri), saved.records)
        assertEquals(Instant.fromEpochMilliseconds(5_000), saved.createdAt)
    }

    @Test
    fun aTemplateNameThatAlreadyExistsGetsANumberedSuffix() = runTest {
        val vm = viewModel()
        vm.addRecord(uri)

        vm.events.test {
            vm.onSaveTemplate("Enlace")
            assertEquals(WriteEvent.TemplateSaved("Enlace"), awaitItem())
            vm.onSaveTemplate("enlace")
            assertEquals(WriteEvent.TemplateSaved("enlace (2)"), awaitItem())
        }
        assertEquals(2, templates.observeAll().first().size)
    }

    // ---- writing: happy path, history, haptic events ---------------------------------------------------------------------

    @Test
    fun writeArmsTheCoordinatorAndASuccessfulTapIsVerifiedAndLogged() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.addRecord(uri)
        val tag = blankTag()

        vm.onWrite()
        assertEquals(WriteSheet.Waiting(messageSize = 16), vm.uiState.value.sheet)

        coordinator.onTagDiscovered(session(tag))
        advanceUntilIdle()

        val sheet = vm.uiState.value.sheet
        assertIs<WriteSheet.Success>(sheet)
        assertEquals(16, sheet.report.messageSize)
        assertEquals(NdefMessage(listOf(NdefRecord.uri("https://example.com"))), userContent(tag))

        val entry = history.observe().first().single()
        assertEquals(HistoryKind.WRITE, entry.kind)
        assertEquals(HistoryOutcome.SUCCESS, entry.outcome)
        assertEquals("https://example.com", entry.summary)
    }

    @Test
    fun theSheetWalksThroughTheWritingPhases() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.addRecord(uri)

        val sheets = mutableListOf<WriteSheet?>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { sheets += it.sheet } }

        vm.onWrite()
        coordinator.onTagDiscovered(session(blankTag()))
        advanceUntilIdle()
        collector.cancel()

        val phases = sheets.filterIsInstance<WriteSheet.Working>().map { it.phase }.distinct()
        assertEquals(
            listOf(OperationPhase.CONNECTING, OperationPhase.READING, OperationPhase.WRITING, OperationPhase.VERIFYING),
            phases,
        )
        assertIs<WriteSheet.Success>(sheets.last())
    }

    @Test
    fun eventsAnnounceDetectionThenSuccess() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.addRecord(uri)

        vm.events.test {
            vm.onWrite()
            coordinator.onTagDiscovered(session(blankTag()))
            advanceUntilIdle()
            assertEquals(WriteEvent.TagDetected, awaitItem())
            assertEquals(WriteEvent.Succeeded, awaitItem())
        }
    }

    @Test
    fun writingIsBlockedWhileNfcIsOffOrMissing() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.addRecord(uri)

        availability.value = NfcAvailability.DISABLED
        assertFalse(vm.uiState.value.canWrite)
        vm.onWrite()
        assertNull(vm.uiState.value.sheet)

        availability.value = NfcAvailability.UNSUPPORTED
        vm.onWrite()
        assertNull(vm.uiState.value.sheet)

        availability.value = NfcAvailability.ENABLED
        assertTrue(vm.uiState.value.canWrite)
    }

    @Test
    fun writingIsBlockedWhileTheMessageIsInvalidOrTooBig() = runTest {
        val vm = viewModel()
        vm.addRecord(RecordSpec.Text("a".repeat(131)))
        vm.onWrite()
        assertNull(vm.uiState.value.sheet)
    }

    // ---- writing: DESTRUCTIVE protocol (G5) ----------------------------------------------------------------------------------

    @Test
    fun existingContentAsksForConfirmationNamingWhatWillBeReplaced() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.addRecord(uri)
        val tag = blankTag()
        runBlockingSeed(tag, NdefMessage(listOf(NdefRecord.text("old note", "en"))))
        val writesBefore = tag.writeCount

        vm.onWrite()
        coordinator.onTagDiscovered(session(tag))
        runCurrent()

        assertEquals(OverwritePrompt("old note"), vm.uiState.value.overwrite)
        assertEquals(writesBefore, tag.writeCount, "nothing is written while the question is open")

        vm.onOverwriteDecision(true)
        advanceUntilIdle()

        assertNull(vm.uiState.value.overwrite)
        assertIs<WriteSheet.Success>(vm.uiState.value.sheet)
        assertEquals(NdefMessage(listOf(NdefRecord.uri("https://example.com"))), userContent(tag))
    }

    @Test
    fun decliningTheOverwriteLeavesTheTagUntouchedAndClosesTheSheetQuietly() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.addRecord(uri)
        val tag = blankTag()
        val old = NdefMessage(listOf(NdefRecord.text("keep me", "en")))
        runBlockingSeed(tag, old)
        val writesBefore = tag.writeCount

        vm.events.test {
            vm.onWrite()
            coordinator.onTagDiscovered(session(tag))
            runCurrent()
            vm.onOverwriteDecision(false)
            advanceUntilIdle()
            assertEquals(WriteEvent.TagDetected, awaitItem())
            expectNoEvents()
        }

        assertEquals(writesBefore, tag.writeCount)
        assertEquals(old, userContent(tag))
        assertNull(vm.uiState.value.sheet)
        assertNull(vm.uiState.value.overwrite)
        assertEquals(0, history.observe().first().size, "a declined overwrite is not a write attempt")
    }

    @Test
    fun dismissingTheSheetWhileTheQuestionIsOpenCountsAsDeclining() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.addRecord(uri)
        val tag = blankTag()
        runBlockingSeed(tag, NdefMessage(listOf(NdefRecord.text("keep me", "en"))))
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

    // ---- writing: failures (G4) -----------------------------------------------------------------------------------------------

    @Test
    fun aTagPulledAwayMidWriteIsAFailureLoggedAsTagLostAndRetryIsOffered() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.addRecord(uri)
        val tag = blankTag().apply { loseTagAfter(3) }

        vm.events.test {
            vm.onWrite()
            coordinator.onTagDiscovered(session(tag))
            advanceUntilIdle()
            assertEquals(WriteEvent.TagDetected, awaitItem())
            assertEquals(WriteEvent.Failed, awaitItem())
            expectNoEvents()
        }

        val sheet = vm.uiState.value.sheet
        assertIs<WriteSheet.Failure>(sheet)
        assertEquals(TagError.TagLost, sheet.error)
        assertTrue(sheet.canRetry)
        assertEquals(HistoryOutcome.FAILED_TAGLOST, history.observe().first().single().outcome)
    }

    @Test
    fun retryingAfterATagLostWritesToTheNextTagTapped() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.addRecord(uri)
        vm.onWrite()
        coordinator.onTagDiscovered(session(blankTag().apply { loseTagAfter(3) }))
        advanceUntilIdle()
        assertIs<WriteSheet.Failure>(vm.uiState.value.sheet)

        vm.onRetry()
        assertEquals(WriteSheet.Waiting(16), vm.uiState.value.sheet)
        val good = blankTag()
        coordinator.onTagDiscovered(session(good))
        advanceUntilIdle()

        assertIs<WriteSheet.Success>(vm.uiState.value.sheet)
        assertEquals(listOf(HistoryOutcome.SUCCESS, HistoryOutcome.FAILED_TAGLOST), history.observe().first().map { it.outcome })
    }

    @Test
    fun aLockedTagIsReadOnlyWithNoRetryLoopAndNothingWritten() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.addRecord(uri)
        val tag = blankTag()
        val open = session(tag)
        val locked = FakeTagSession(open.info.copy(ndef = open.info.ndef!!.copy(isWritable = false)), tag)

        vm.onWrite()
        coordinator.onTagDiscovered(locked)
        advanceUntilIdle()

        val sheet = vm.uiState.value.sheet
        assertIs<WriteSheet.Failure>(sheet)
        assertEquals(TagError.ReadOnly, sheet.error)
        assertFalse(sheet.canRetry)
        assertEquals(0, tag.writeCount)
        assertEquals(HistoryOutcome.FAILED_READ_ONLY, history.observe().first().single().outcome)
    }

    @Test
    fun aTagThatIsTooSmallFailsBeforeWritingAndSwitchesTheMeterToThatTag() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onTargetSelected(WriteTarget.Chip(NtagModel.NTAG215))   // the user believed they had a 215...
        vm.addRecord(RecordSpec.Text("a".repeat(200)))             // 207 bytes: fits a 215, not a 213
        val tag = blankTag(NtagModel.NTAG213)                      // ...but tapped a 213

        vm.onWrite()
        coordinator.onTagDiscovered(session(tag))
        advanceUntilIdle()

        val state = vm.uiState.value
        val sheet = state.sheet
        assertIs<WriteSheet.Failure>(sheet)
        assertEquals(TagError.CapacityExceeded(required = 207, available = 137), sheet.error)
        assertFalse(sheet.canRetry)
        assertEquals(0, tag.writeCount)
        assertEquals(137, state.lastTagCapacity)
        assertEquals(WriteTarget.LastTag(137), state.target)
        assertEquals(WriteBlock.OVER_CAPACITY, state.analysis.block)
    }

    // ---- writing: sheet lifecycle -----------------------------------------------------------------------------------------------

    @Test
    fun dismissingTheWaitingSheetDisarmsSoTagsAreIgnored() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.addRecord(uri)
        val tag = blankTag()

        vm.onWrite()
        vm.onSheetDismissed()
        coordinator.onTagDiscovered(session(tag))
        advanceUntilIdle()

        assertNull(vm.uiState.value.sheet)
        assertEquals(0, tag.writeCount)
    }

    /** A session whose NDEF read blocks until the test opens the gate, freezing the operation mid-flight. */
    private class GatedSession(private val inner: FakeTagSession, val gate: CompletableDeferred<Unit>) : TagSession by inner {
        override suspend fun readNdef(): TagResult<NdefMessage?> {
            gate.await()
            return inner.readNdef()
        }
    }

    @Test
    fun theSheetCannotBeDismissedWhileTheTagIsBeingWritten() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.addRecord(uri)
        val tag = blankTag()
        val gated = GatedSession(session(tag), CompletableDeferred())

        vm.onWrite()
        coordinator.onTagDiscovered(gated)
        runCurrent()
        val working = vm.uiState.value.sheet
        assertIs<WriteSheet.Working>(working)

        vm.onSheetDismissed()
        assertEquals(working, vm.uiState.value.sheet, "dismissing mid-write would abort the write and risk the tag")

        gated.gate.complete(Unit)
        advanceUntilIdle()
        assertIs<WriteSheet.Success>(vm.uiState.value.sheet)
        assertEquals(NdefMessage(listOf(NdefRecord.uri("https://example.com"))), userContent(tag))
    }

    @Test
    fun leavingTheScreenWhileWaitingDisarmsSoNoTagIsWrittenInTheBackground() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.addRecord(uri)
        val tag = blankTag()

        vm.onWrite()
        vm.onHidden()
        coordinator.onTagDiscovered(session(tag))
        advanceUntilIdle()

        assertNull(vm.uiState.value.sheet)
        assertEquals(0, tag.writeCount)
    }

    @Test
    fun leavingTheScreenWhileTheTagIsBeingWrittenDoesNotAbortTheWrite() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.addRecord(uri)
        val tag = blankTag()
        val gated = GatedSession(session(tag), CompletableDeferred())

        vm.onWrite()
        coordinator.onTagDiscovered(gated)
        runCurrent()
        vm.onHidden()
        gated.gate.complete(Unit)
        advanceUntilIdle()

        assertIs<WriteSheet.Success>(vm.uiState.value.sheet)
        assertEquals(NdefMessage(listOf(NdefRecord.uri("https://example.com"))), userContent(tag))
    }

    @Test
    fun writeAnotherReArmsTheSameMessageForTheNextTag() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.addRecord(uri)
        vm.onWrite()
        coordinator.onTagDiscovered(session(blankTag()))
        advanceUntilIdle()
        assertIs<WriteSheet.Success>(vm.uiState.value.sheet)

        vm.onWriteAnother()
        assertEquals(WriteSheet.Waiting(16), vm.uiState.value.sheet)
        val second = blankTag(NtagModel.NTAG215)
        coordinator.onTagDiscovered(session(second))
        advanceUntilIdle()

        assertIs<WriteSheet.Success>(vm.uiState.value.sheet)
        assertEquals(NdefMessage(listOf(NdefRecord.uri("https://example.com"))), userContent(second))
        assertEquals(2, history.observe().first().size)
    }

    @Test
    fun doneClosesTheSuccessSheetAndKeepsTheRecordsForReuse() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.addRecord(uri)
        vm.onWrite()
        coordinator.onTagDiscovered(session(blankTag()))
        advanceUntilIdle()

        vm.onSheetDismissed()

        assertNull(vm.uiState.value.sheet)
        assertEquals(listOf<RecordSpec>(uri), vm.uiState.value.records)
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------------------

    private fun runBlockingSeed(tag: FakeNtag21x, message: NdefMessage) {
        kotlinx.coroutines.runBlocking { session(tag).writeNdef(message) }
    }


    @Test
    fun hidingDoesNotCancelTheOperationOfTheScreenThatArrivedFirst() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.addRecord(uri)
        vm.onWrite()
        val next = coordinator.arm(com.tagforge.domain.read.ReadTagUseCase())   // navigation arms the new screen before the old one stops

        vm.onHidden()
        coordinator.onTagDiscovered(session(blankTag()))
        advanceUntilIdle()

        assertIs<com.tagforge.domain.scan.ScanState.Success<*>>(next.value, "the arriving screen still got its tag")
    }
}
