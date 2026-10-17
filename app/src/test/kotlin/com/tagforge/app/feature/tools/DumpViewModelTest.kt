package com.tagforge.app.feature.tools

import app.cash.turbine.test
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ntag.Region
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

/** TT-03 annotated dump screen state. The dump itself is covered by DumpMemoryUseCaseTest. */
@OptIn(ExperimentalCoroutinesApi::class)
class DumpViewModelTest {

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

    private fun viewModel(coordinator: ScanCoordinator) = DumpViewModel(coordinator, availability)

    private fun tag(model: NtagModel = NtagModel.NTAG213): FakeNtag21x =
        FakeNtag21x(model).also { runBlocking { FakeTagSession.ntag(it).writeNdef(NdefMessage(listOf(NdefRecord.text("hi", "en")))) } }

    private fun session(tag: FakeNtag21x) = FakeTagSession.ntag(tag)

    // ---- reading ----------------------------------------------------------------------------------------------------

    @Test
    fun becomingVisibleArmsTheDumpAndATapProducesTheAnnotatedResult() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)

        vm.events.test {
            vm.onVisible()
            assertEquals(ToolReading.Waiting, vm.uiState.value.reading)
            coordinator.onTagDiscovered(session(tag()))
            advanceUntilIdle()
            assertEquals(ToolReadEvent.TagDetected, awaitItem())
            assertEquals(ToolReadEvent.Succeeded, awaitItem())
            expectNoEvents()
        }

        val reading = vm.uiState.value.reading
        assertIs<ToolReading.Result<*>>(reading)
        val report = vm.uiState.value.report!!
        assertEquals(45, report.dump.pages.size)
        assertEquals(Region.PACK, report.dump.annotations.last().region)
    }

    @Test
    fun leavingWhileWaitingDisarmsAndComingBackArmsAgain() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onVisible()
        vm.onHidden()

        coordinator.onTagDiscovered(session(tag()))
        advanceUntilIdle()
        assertEquals(ToolReading.Waiting, vm.uiState.value.reading, "a hidden screen reads nothing")

        vm.onVisible()
        coordinator.onTagDiscovered(session(tag()))
        advanceUntilIdle()
        assertIs<ToolReading.Result<*>>(vm.uiState.value.reading)
    }

    /** A session whose identification blocks until the test opens the gate, freezing the operation mid-flight. */
    private class GatedSession(private val inner: FakeTagSession, val gate: CompletableDeferred<Unit>) : TagSession by inner {
        override suspend fun identify() = inner.identify().also { gate.await() }
    }

    @Test
    fun leavingWhileReadingDoesNotAbortTheRead() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val gated = GatedSession(session(tag()), CompletableDeferred())

        vm.onVisible()
        coordinator.onTagDiscovered(gated)
        runCurrent()
        assertIs<ToolReading.Reading>(vm.uiState.value.reading)
        vm.onHidden()
        gated.gate.complete(Unit)
        advanceUntilIdle()

        assertIs<ToolReading.Result<*>>(vm.uiState.value.reading)
    }

    @Test
    fun aProtectedTagStillGivesAResultWithTheUnreadableRange() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val protected = tag()
        protected.setPage(NtagModel.NTAG213.cfg0, "00 00 00 04".hex())
        protected.setPage(NtagModel.NTAG213.cfg1, "80 00 00 00".hex())

        vm.onVisible()
        coordinator.onTagDiscovered(session(protected))
        advanceUntilIdle()

        assertEquals(4..44, vm.uiState.value.report!!.dump.unreadable)
    }

    @Test
    fun aTagPulledAwayIsAnErrorAndRescanReArms() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val lost = tag().apply { failNextAfter(1, TagError.TagLost) }

        vm.events.test {
            vm.onVisible()
            coordinator.onTagDiscovered(session(lost))
            advanceUntilIdle()
            assertEquals(ToolReadEvent.TagDetected, awaitItem())
            assertEquals(ToolReadEvent.Failed, awaitItem())
        }
        val reading = vm.uiState.value.reading
        assertIs<ToolReading.Error>(reading)
        assertEquals(TagError.TagLost, reading.error)

        vm.onRescan()
        assertEquals(ToolReading.Waiting, vm.uiState.value.reading)
        coordinator.onTagDiscovered(session(tag()))
        advanceUntilIdle()
        assertIs<ToolReading.Result<*>>(vm.uiState.value.reading)
    }

    @Test
    fun mifareClassicIsAnExplainedErrorNotACrash() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)

        vm.onVisible()
        coordinator.onTagDiscovered(FakeTagSession.withoutNtag(TagFamily.MIFARE_CLASSIC))
        advanceUntilIdle()

        val reading = vm.uiState.value.reading
        assertIs<ToolReading.Error>(reading)
        assertEquals(TagError.Unsupported(TagError.Unsupported.MIFARE_CLASSIC), reading.error)
    }

    @Test
    fun nothingIsArmedWhileNfcIsOff() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        availability.value = NfcAvailability.DISABLED

        vm.onVisible()
        val t = tag()
        coordinator.onTagDiscovered(session(t))
        advanceUntilIdle()

        assertEquals(ToolReading.Waiting, vm.uiState.value.reading)
        assertEquals(NfcAvailability.DISABLED, vm.uiState.value.availability)
    }

    // ---- presentation state --------------------------------------------------------------------------------------------

    @Test
    fun theLayoutToggleAndTheSelectedRowAreKeptInState() = runTest {
        val vm = viewModel(coordinator())

        assertEquals(DumpLayout.PAGE_PER_ROW, vm.uiState.value.layout)
        vm.onLayoutChanged(DumpLayout.SIXTEEN_BYTES)
        assertEquals(DumpLayout.SIXTEEN_BYTES, vm.uiState.value.layout)

        assertNull(vm.uiState.value.selectedPage)
        vm.onPageSelected(41)
        assertEquals(41, vm.uiState.value.selectedPage)
        vm.onPageSelected(null)
        assertNull(vm.uiState.value.selectedPage)
    }

    @Test
    fun selectionAndLayoutSurviveARescan() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onLayoutChanged(DumpLayout.SIXTEEN_BYTES)

        vm.onVisible()
        coordinator.onTagDiscovered(session(tag()))
        advanceUntilIdle()
        vm.onRescan()

        assertEquals(DumpLayout.SIXTEEN_BYTES, vm.uiState.value.layout)
    }

    // ---- row building (pure) ---------------------------------------------------------------------------------------------

    private fun dumpOf(model: NtagModel): com.tagforge.domain.ntag.MemoryDump =
        com.tagforge.domain.ntag.MemoryDump(model, (0 until model.totalPages).map { Bytes.of(it, 0, 0, 0) }, null)

    @Test
    fun pagePerRowGivesOneRowPerPageWithItsRegion() {
        val rows = DumpRows.build(dumpOf(NtagModel.NTAG213), DumpLayout.PAGE_PER_ROW)

        assertEquals(45, rows.size)
        assertEquals(listOf(0), rows[0].pages)
        assertEquals(Region.UID, rows[0].primaryRegion)
        assertEquals(Region.USER, rows[4].primaryRegion)
        assertEquals(Region.PWD, rows[43].primaryRegion)
        assertTrue(rows[43].hiddenByChip)
        assertEquals("00 00 00 00".hex(), rows[43].bytes)
        assertFalse(rows[42].hiddenByChip)
    }

    @Test
    fun sixteenBytesGroupsFourPagesAndTheLastRowMayBeShort() {
        val rows = DumpRows.build(dumpOf(NtagModel.NTAG213), DumpLayout.SIXTEEN_BYTES)

        assertEquals(12, rows.size)
        assertEquals(listOf(0, 1, 2, 3), rows[0].pages)
        assertEquals(16, rows[0].bytes.size)
        assertEquals(listOf(44), rows.last().pages)
        assertEquals(listOf(Region.UID, Region.UID, Region.UID, Region.CC), rows[0].regions.map { it.first() })
    }

    @Test
    fun aRowThatHoldsAHiddenPageShowsZerosForIt() {
        val rows = DumpRows.build(dumpOf(NtagModel.NTAG213), DumpLayout.SIXTEEN_BYTES)

        val configRow = rows.single { 43 in it.pages }   // pages 40..43: dyn lock, cfg0, cfg1, PWD
        assertEquals(listOf(40, 41, 42, 43), configRow.pages)
        assertEquals(Bytes.of(40, 0, 0, 0, 41, 0, 0, 0, 42, 0, 0, 0, 0, 0, 0, 0), configRow.bytes)
        assertTrue(configRow.hiddenByChip)
    }

    @Test
    fun nonNtagDumpsHaveNoRegionsAndNothingHidden() {
        val dump = com.tagforge.domain.ntag.MemoryDump(null, (0 until 16).map { Bytes.of(it, 1, 2, 3) }, null)

        val rows = DumpRows.build(dump, DumpLayout.PAGE_PER_ROW)

        assertEquals(16, rows.size)
        assertNull(rows[3].primaryRegion)
        assertFalse(rows.any { it.hiddenByChip })
    }
}
