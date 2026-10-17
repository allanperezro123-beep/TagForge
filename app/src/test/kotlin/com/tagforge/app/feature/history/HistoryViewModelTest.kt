package com.tagforge.app.feature.history

import app.cash.turbine.test
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.InMemoryHistoryRepository
import com.tagforge.domain.fakes.InMemoryTemplateRepository
import com.tagforge.domain.history.HistoryEntry
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryOutcome
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.ParsedRecord
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.template.Template
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import kotlin.time.Instant

/** HT-01 list, HT-05 clear with confirmation, AS-06 loading and empty states, TagDetail rendering. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val history = InMemoryHistoryRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun entry(at: Long, summary: String = "entry $at", ndef: Bytes? = null) = HistoryEntry(
        at = Instant.fromEpochMilliseconds(at),
        kind = HistoryKind.READ,
        outcome = HistoryOutcome.SUCCESS,
        uid = Bytes.of(4, 1, 2, 3),
        tagType = "NTAG213",
        summary = summary,
        ndef = ndef,
    )

    // ---- list ---------------------------------------------------------------------------------------------

    @Test
    fun startsLoadingThenShowsNewestFirst() = runTest {
        history.add(entry(1_000))
        history.add(entry(3_000))
        history.add(entry(2_000))

        HistoryViewModel(history).uiState.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            assertFalse(state.loading)
            assertEquals(listOf(3_000L, 2_000L, 1_000L), state.entries.map { it.at.toEpochMilliseconds() })
        }
    }

    @Test
    fun noEntriesIsAnEmptyStateNotAnEndlessSkeleton() = runTest {
        HistoryViewModel(history).uiState.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            assertFalse(state.loading)
            assertTrue(state.isEmpty)
        }
    }

    @Test
    fun newEntriesAppearWithoutReloading() = runTest {
        val vm = HistoryViewModel(history)
        vm.uiState.test {
            skipItems(1)   // loading or already-empty
            history.add(entry(1_000))
            val state = expectMostRecentItem()
            assertEquals(1, state.entries.size)
        }
    }

    // ---- clearing (HT-05) -----------------------------------------------------------------------------------

    @Test
    fun clearingAsksForConfirmationFirstAndCancelLeavesEverythingAlone() = runTest {
        history.add(entry(1_000))
        val vm = HistoryViewModel(history)
        vm.uiState.test {
            awaitItem()
            vm.onClearRequested()
            assertTrue(expectMostRecentItem().confirmClear)

            vm.onClearDismissed()
            val after = expectMostRecentItem()
            assertFalse(after.confirmClear)
            assertEquals(1, after.entries.size)
        }
        assertEquals(1, history.observe().first().size)
    }

    @Test
    fun confirmingClearRemovesTheHistoryButNeverTheTemplates() = runTest {
        val templates = InMemoryTemplateRepository()
        templates.upsert(Template(name = "WiFi casa", records = listOf(RecordSpec.Text("x")), createdAt = Instant.fromEpochMilliseconds(1), updatedAt = Instant.fromEpochMilliseconds(1)))
        history.add(entry(1_000))
        history.add(entry(2_000))
        val vm = HistoryViewModel(history)

        vm.onClearRequested()
        vm.onClearConfirmed()

        assertEquals(0, history.observe().first().size)
        assertEquals(1, templates.observeAll().first().size)
        assertFalse(vm.uiState.value.confirmClear)
    }

    // ---- deleting one entry, with undo ------------------------------------------------------------------------

    @Test
    fun deletingOneEntryCanBeUndoneIntoTheSameRow() = runTest {
        val id = history.add(entry(1_000, "keep me"))
        history.add(entry(2_000))
        val vm = HistoryViewModel(history)

        vm.onDelete(id)
        assertEquals(listOf(2_000L), history.observe().first().map { it.at.toEpochMilliseconds() })
        assertEquals("keep me", vm.uiState.value.lastDeleted?.summary)

        vm.onUndoDelete()
        val restored = history.observe().first()
        assertEquals(listOf(2_000L, 1_000L), restored.map { it.at.toEpochMilliseconds() })
        assertEquals(id, restored.last().id)
        assertEquals(null, vm.uiState.value.lastDeleted)
    }

    // ---- stored readout (TagDetailRoute) ------------------------------------------------------------------------

    @Test
    fun theDetailParsesTheStoredNdefLikeAFreshRead() = runTest {
        val message = NdefMessage(listOf(NdefRecord.text("hola", "es"), NdefRecord.uri("https://example.com")))
        val id = history.add(entry(1_000, ndef = DefaultNdefCodec.encode(message)))

        TagDetailViewModel(id, history).uiState.test {
            var state = awaitItem()
            if (state is TagDetailUiState.Loading) state = awaitItem()
            assertIs<TagDetailUiState.Loaded>(state)
            assertEquals(listOf("Text", "Uri"), state.records.map { it::class.simpleName })
            assertEquals("hola", (state.records[0] as ParsedRecord.Text).text)
            assertEquals(false, state.unreadable)
        }
    }

    @Test
    fun theDetailOfAnEntryWithoutNdefHasNoRecordsAndIsNotUnreadable() = runTest {
        val id = history.add(entry(1_000, ndef = null))
        val state = TagDetailViewModel(id, history).awaitLoaded()
        assertEquals(emptyList(), state.records)
        assertFalse(state.unreadable)
    }

    @Test
    fun corruptStoredBytesAreFlaggedInsteadOfCrashing() = runTest {
        val id = history.add(entry(1_000, ndef = Bytes.of(0xD1, 0x01, 0x05, 0x54)))
        val state = TagDetailViewModel(id, history).awaitLoaded()
        assertTrue(state.unreadable)
        assertEquals(emptyList(), state.records)
    }

    @Test
    fun aMissingEntryIsNotFound() = runTest {
        TagDetailViewModel(999, history).uiState.test {
            var state = awaitItem()
            if (state is TagDetailUiState.Loading) state = awaitItem()
            assertEquals(TagDetailUiState.NotFound, state)
        }
    }

    private suspend fun TagDetailViewModel.awaitLoaded(): TagDetailUiState.Loaded {
        var result: TagDetailUiState.Loaded? = null
        uiState.test {
            var state = awaitItem()
            if (state is TagDetailUiState.Loading) state = awaitItem()
            result = state as TagDetailUiState.Loaded
        }
        return result!!
    }
}
