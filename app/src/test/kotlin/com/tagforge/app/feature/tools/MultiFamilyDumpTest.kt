package com.tagforge.app.feature.tools

import app.cash.turbine.test
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.FakeChipSession
import com.tagforge.domain.fakes.FakeClassicSession
import com.tagforge.domain.fakes.FakeIso15693
import com.tagforge.domain.fakes.FakeMifareClassic
import com.tagforge.domain.fakes.FakeNfcVSession
import com.tagforge.domain.fakes.FakeType2Chip
import com.tagforge.domain.ntag.MemoryDump
import com.tagforge.domain.ntag.PageAnnotation
import com.tagforge.domain.ntag.Region
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.type2.Type2Chip
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The dump screen state for the families added in 1.1.0: block sizes other than 4 bytes, locked blocks, new roles. */
@OptIn(ExperimentalCoroutinesApi::class)
class MultiFamilyDumpTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.dumpOf(session: TagSession): DumpUiState {
        val coordinator = ScanCoordinator(this, StandardTestDispatcher(testScheduler))
        val vm = DumpViewModel(coordinator, MutableStateFlow(NfcAvailability.ENABLED))
        vm.onVisible()
        coordinator.onTagDiscovered(session)
        advanceUntilIdle()
        return vm.uiState.value
    }

    @Test
    fun aClassicDumpShowsOneSixteenByteBlockPerRowEvenInTheFourPageLayout() = runTest {
        val state = dumpOf(FakeClassicSession())
        val rows = DumpRows.build(state.report!!.dump, DumpLayout.SIXTEEN_BYTES)
        assertEquals(64, rows.size)
        assertTrue(rows.all { it.bytes.size == 16 })
        assertEquals(Region.SECTOR_TRAILER, rows[3].primaryRegion)
        assertEquals(Region.UID, rows[0].primaryRegion)
    }

    @Test
    fun aLockedClassicSectorIsFlaggedUnreadableOnItsRowsOnly() = runTest {
        val card = FakeMifareClassic().also { it.setKeys(2, Bytes.of(1, 2, 3, 4, 5, 6), Bytes.of(1, 2, 3, 4, 5, 6)) }
        val rows = DumpRows.build(dumpOf(FakeClassicSession(card)).report!!.dump, DumpLayout.PAGE_PER_ROW)
        assertEquals((8..11).toList(), rows.filter { it.unreadable }.map { it.startPage })
    }

    @Test
    fun anUltralightCDumpGetsItsOwnRolesAndHidesTheKeyAsZeros() = runTest {
        val tag = FakeType2Chip(Type2Chip.ULTRALIGHT_C).also { it.setPage(44, Bytes.of(9, 9, 9, 9)) }
        val rows = DumpRows.build(dumpOf(FakeChipSession(tag)).report!!.dump, DumpLayout.PAGE_PER_ROW)
        assertEquals(48, rows.size)
        assertEquals(Region.KEY, rows[44].primaryRegion)
        assertTrue(rows[44].hiddenByChip)
        assertEquals(Bytes.of(0, 0, 0, 0), rows[44].bytes)
        assertEquals(Region.AUTH_CONFIG, rows[42].primaryRegion)
    }

    @Test
    fun anIso15693DumpWithAFourByteBlockStillGroupsFourBlocksInTheWideLayout() = runTest {
        val tag = FakeIso15693(blockCount = 8).also { it.setBlock(0, Bytes.of(0xE1, 0x40, 0x04, 0x01)) }
        val dump = dumpOf(FakeNfcVSession(tag)).report!!.dump
        assertEquals(2, DumpRows.build(dump, DumpLayout.SIXTEEN_BYTES).size)
        assertEquals(Region.CC, DumpRows.build(dump, DumpLayout.PAGE_PER_ROW).first().primaryRegion)
    }

    @Test
    fun rowsOfAPlainDumpWithoutAnnotationsHaveNoRegions() {
        val dump = MemoryDump(null, listOf(Bytes.of(1, 2, 3, 4), Bytes.of(5, 6, 7, 8)), null)
        val rows = DumpRows.build(dump, DumpLayout.PAGE_PER_ROW)
        assertEquals(listOf(null, null), rows.map { it.primaryRegion })
        assertFalse(rows.any { it.unreadable })
    }

    @Test
    fun customAnnotationsWinOverEverythingElse() {
        val dump = MemoryDump(null, listOf(Bytes.of(1, 2, 3, 4)), null, null, listOf(PageAnnotation(0, listOf(Region.CC))))
        assertEquals(Region.CC, DumpRows.build(dump, DumpLayout.PAGE_PER_ROW).single().primaryRegion)
    }

    @Test
    fun theDumpOfAnUnsupportedFamilyEndsAsAnErrorNotAResult() = runTest {
        val coordinator = ScanCoordinator(this, StandardTestDispatcher(testScheduler))
        val vm = DumpViewModel(coordinator, MutableStateFlow(NfcAvailability.ENABLED))
        vm.events.test {
            vm.onVisible()
            coordinator.onTagDiscovered(com.tagforge.domain.fakes.FakeTagSession.withoutNtag())
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        val reading = vm.uiState.value.reading
        assertIs<ToolReading.Error>(reading)
        assertEquals(com.tagforge.domain.tag.TagError.Unsupported(com.tagforge.domain.tag.TagError.Unsupported.MIFARE_CLASSIC), reading.error)
    }
}
