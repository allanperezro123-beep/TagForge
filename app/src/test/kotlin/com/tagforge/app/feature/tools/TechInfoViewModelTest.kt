package com.tagforge.app.feature.tools

import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.tag.IsoDepDetails
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech
import com.tagforge.domain.tag.TechDetails
import com.tagforge.domain.tag.TechValue
import com.tagforge.domain.tag.DetailKey
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

/** TT-05: the technology screen shows what the OS reported for an IsoDep card, with no writes anywhere. */
@OptIn(ExperimentalCoroutinesApi::class)
class TechInfoViewModelTest {

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
    fun anIsoDepCardShowsItsHistoricalBytesInHex() = runTest {
        val coordinator = coordinator()
        val vm = TechInfoViewModel(coordinator, availability)
        val details = TechDetails(isoDep = IsoDepDetails(hiLayerResponse = null, extendedLengthSupported = true, timeoutMs = 618))
        val info = TagInfo(
            uid = "08 11 22 33".hex(),
            techs = setOf(TagTech.NFC_A, TagTech.ISO_DEP),
            family = TagFamily.ISO14443_4,
            atqa = "03 44".hex(),
            sak = 0x20,
            historicalBytes = "78 77 80 02".hex(),
            details = details,
        )

        vm.onVisible()
        coordinator.onTagDiscovered(FakeTagSession(info))
        advanceUntilIdle()

        val reading = vm.uiState.value.reading
        assertIs<ToolReading.Result<TagInfo>>(reading)
        val isoDep = reading.value.details.sections(reading.value).single { it.tech == TagTech.ISO_DEP }
        assertEquals(TechValue.Text("78 77 80 02"), isoDep.entries.single { it.key == DetailKey.HISTORICAL_BYTES }.value)
    }

    @Test
    fun anNtagIsIdentifiedByChipModelAndNothingIsWritten() = runTest {
        val coordinator = coordinator()
        val vm = TechInfoViewModel(coordinator, availability)
        val tag = FakeNtag21x(NtagModel.NTAG216)

        vm.onVisible()
        coordinator.onTagDiscovered(FakeTagSession.ntag(tag, identified = false))
        advanceUntilIdle()

        val reading = vm.uiState.value.reading
        assertIs<ToolReading.Result<TagInfo>>(reading)
        assertEquals(NtagModel.NTAG216, reading.value.ntag)
        assertEquals(0, tag.writeCount)
    }

    @Test
    fun rescanningWaitsForTheNextTag() = runTest {
        val coordinator = coordinator()
        val vm = TechInfoViewModel(coordinator, availability)
        vm.onVisible()
        coordinator.onTagDiscovered(FakeTagSession.ntag(FakeNtag21x(NtagModel.NTAG213), identified = false))
        advanceUntilIdle()

        vm.onRescan()

        assertEquals(ToolReading.Waiting, vm.uiState.value.reading)
    }

    // ---- ownership: leaving must not cancel the screen we navigated to ------------------------------------------

    @Test
    fun hidingDoesNotCancelTheOperationOfTheScreenThatArrivedFirst() = runTest {
        val coordinator = coordinator()
        val vm = TechInfoViewModel(coordinator, availability)
        vm.onVisible()
        val next = coordinator.arm(com.tagforge.domain.read.ReadTagUseCase())   // navigation arms the new screen before the old one stops

        vm.onHidden()
        coordinator.onTagDiscovered(FakeTagSession.ntag(FakeNtag21x(NtagModel.NTAG213)))
        advanceUntilIdle()

        assertIs<com.tagforge.domain.scan.ScanState.Success<*>>(next.value, "the arriving screen still got its tag")
    }
}
