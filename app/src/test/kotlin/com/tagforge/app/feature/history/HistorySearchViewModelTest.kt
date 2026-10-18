package com.tagforge.app.feature.history

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.FixedClock
import com.tagforge.domain.fakes.InMemoryHistoryRepository
import com.tagforge.domain.history.HistoryEntry
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryOutcome
import com.tagforge.domain.history.HistoryQuery
import com.tagforge.domain.history.OutcomeFilter
import com.tagforge.domain.ports.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** HT-03: debounced search (no Enter), kind/outcome/date chips, combined filters, "no results" with clear filters. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistorySearchViewModelTest {

    private val history = InMemoryHistoryRepository()
    private val clock = FixedClock(Instant.fromEpochMilliseconds(DAY * 100))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun add(
        at: Long,
        summary: String = "entry $at",
        kind: HistoryKind = HistoryKind.READ,
        outcome: HistoryOutcome = HistoryOutcome.SUCCESS,
    ) = history.add(
        HistoryEntry(
            at = Instant.fromEpochMilliseconds(at),
            kind = kind,
            outcome = outcome,
            uid = Bytes.of(4, 1, 2, 3),
            tagType = "NTAG213",
            summary = summary,
            ndef = null,
        ),
    )

    private fun HistoryViewModel.summaries() = uiState.value.entries.map { it.summary }

    // ---- text: debounced, no Enter -----------------------------------------------------------------------------

    @Test
    fun typingFiltersAfterTheDebounceWithoutPressingEnter() = runTest {
        add(1, "WiFi Casa")
        add(2, "https://example.com")
        val vm = HistoryViewModel(history, clock)
        runCurrent()
        assertEquals(2, vm.uiState.value.entries.size)

        vm.onSearchChanged("wifi")
        assertEquals("wifi", vm.uiState.value.filters.text)           // the field shows what was typed at once
        assertEquals(2, vm.uiState.value.entries.size)                // ...but the list waits for a pause in typing
        advanceTimeBy(HistoryViewModel.SEARCH_DEBOUNCE_MS - 1)
        assertEquals(2, vm.uiState.value.entries.size)

        advanceTimeBy(2)
        assertEquals(listOf("WiFi Casa"), vm.summaries())
    }

    @Test
    fun onlyTheLastKeystrokeOfABurstReachesTheRepository() = runTest {
        add(1, "WiFi Casa")
        val queries = mutableListOf<HistoryQuery>()
        val spy = object : HistoryRepository by history {
            override fun observe(query: HistoryQuery): Flow<List<HistoryEntry>> =
                history.observe(query).onStart { queries += query }
        }
        val vm = HistoryViewModel(spy, clock)
        runCurrent()

        for (typed in listOf("w", "wi", "wif", "wifi")) {
            vm.onSearchChanged(typed)
            advanceTimeBy(50)
        }
        advanceTimeBy(HistoryViewModel.SEARCH_DEBOUNCE_MS)

        assertEquals(listOf("", "wifi"), queries.map { it.text })
        assertEquals(listOf("WiFi Casa"), vm.summaries())
    }

    @Test
    fun clearingTheTextRestoresTheWholeListAtOnce() = runTest {
        add(1, "WiFi Casa")
        add(2, "https://example.com")
        val vm = HistoryViewModel(history, clock)
        vm.onSearchChanged("wifi")
        advanceTimeBy(HistoryViewModel.SEARCH_DEBOUNCE_MS + 1)
        assertEquals(1, vm.uiState.value.entries.size)

        vm.onSearchChanged("")
        runCurrent()

        assertEquals(2, vm.uiState.value.entries.size)
    }

    // ---- chips: kind, outcome, date -----------------------------------------------------------------------------

    @Test
    fun kindChipsToggleAndApplyAtOnce() = runTest {
        add(1, "read one", HistoryKind.READ)
        add(2, "write one", HistoryKind.WRITE)
        add(3, "erase one", HistoryKind.ERASE)
        val vm = HistoryViewModel(history, clock)

        vm.onToggleKind(HistoryKind.WRITE)
        assertEquals(listOf("write one"), vm.summaries())
        vm.onToggleKind(HistoryKind.ERASE)
        assertEquals(listOf("erase one", "write one"), vm.summaries())
        vm.onToggleKind(HistoryKind.WRITE)
        vm.onToggleKind(HistoryKind.ERASE)
        assertEquals(3, vm.uiState.value.entries.size)
    }

    @Test
    fun tenEntriesWithThreeFailedWritesShowExactlyThreeWhenFiltered() = runTest {
        repeat(6) { add(it + 1L, kind = HistoryKind.READ) }
        repeat(3) { add(100L + it, kind = HistoryKind.WRITE, outcome = HistoryOutcome.FAILED_TAGLOST) }
        add(200, kind = HistoryKind.WRITE, outcome = HistoryOutcome.SUCCESS)
        val vm = HistoryViewModel(history, clock)

        vm.onToggleKind(HistoryKind.WRITE)
        vm.onOutcomeChanged(OutcomeFilter.FAILED)

        assertEquals(3, vm.uiState.value.entries.size)
        assertTrue(vm.uiState.value.entries.all { it.kind == HistoryKind.WRITE && !it.outcome.isSuccess })
    }

    @Test
    fun dateRangeKeepsOnlyRecentEntriesRelativeToTheClock() = runTest {
        val now = clock.now().toEpochMilliseconds()
        add(now - 40 * DAY, "forty days ago")
        add(now - 10 * DAY, "ten days ago")
        add(now - 2 * DAY, "two days ago")
        add(now - 2 * HOUR, "two hours ago")
        val vm = HistoryViewModel(history, clock)

        vm.onRangeChanged(DateRange.DAY)
        assertEquals(listOf("two hours ago"), vm.summaries())
        vm.onRangeChanged(DateRange.WEEK)
        assertEquals(listOf("two hours ago", "two days ago"), vm.summaries())
        vm.onRangeChanged(DateRange.MONTH)
        assertEquals(listOf("two hours ago", "two days ago", "ten days ago"), vm.summaries())
        vm.onRangeChanged(DateRange.ANY)
        assertEquals(4, vm.uiState.value.entries.size)
    }

    // ---- empty vs. no results ------------------------------------------------------------------------------------

    @Test
    fun aFilterWithNoMatchesIsNoResultsNotTheEmptyHistoryState() = runTest {
        add(1, "WiFi Casa")
        val vm = HistoryViewModel(history, clock)

        vm.onSearchChanged("zzz")
        advanceTimeBy(HistoryViewModel.SEARCH_DEBOUNCE_MS + 1)

        val state = vm.uiState.value
        assertTrue(state.noResults)
        assertFalse(state.isEmpty)
        assertTrue(state.filters.isActive)
    }

    @Test
    fun noEntriesAtAllIsTheEmptyStateAndNotNoResults() = runTest {
        val vm = HistoryViewModel(history, clock)
        runCurrent()
        assertTrue(vm.uiState.value.isEmpty)
        assertFalse(vm.uiState.value.noResults)
    }

    @Test
    fun clearFiltersResetsEveryCriterionAndShowsTheListAgain() = runTest {
        add(1, "WiFi Casa", HistoryKind.WRITE)
        add(2, "https://example.com", HistoryKind.READ)
        val vm = HistoryViewModel(history, clock)
        vm.onSearchChanged("zzz")
        vm.onToggleKind(HistoryKind.ERASE)
        vm.onOutcomeChanged(OutcomeFilter.FAILED)
        vm.onRangeChanged(DateRange.DAY)
        advanceTimeBy(HistoryViewModel.SEARCH_DEBOUNCE_MS + 1)
        assertTrue(vm.uiState.value.noResults)

        vm.onClearFilters()
        runCurrent()

        val state = vm.uiState.value
        assertFalse(state.filters.isActive)
        assertEquals("", state.filters.text)
        assertEquals(2, state.entries.size)
        assertFalse(state.noResults)
    }

    companion object {
        private const val HOUR = 3_600_000L
        private const val DAY = 24 * HOUR
    }
}
