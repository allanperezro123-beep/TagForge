package com.tagforge.app.feature.templates

import app.cash.turbine.test
import com.tagforge.domain.fakes.FixedClock
import com.tagforge.domain.fakes.InMemoryTemplateRepository
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
import kotlin.test.assertTrue
import kotlin.time.Instant

/** HT-02: list, rename (with collision rules), delete with Undo, and the loading/empty states of AS-06. */
@OptIn(ExperimentalCoroutinesApi::class)
class TemplatesViewModelTest {

    private val repository = InMemoryTemplateRepository()
    private val clock = FixedClock(Instant.fromEpochMilliseconds(9_000))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun add(name: String, vararg records: RecordSpec): Long =
        repository.upsert(Template(name = name, records = records.toList(), createdAt = Instant.fromEpochMilliseconds(1), updatedAt = Instant.fromEpochMilliseconds(1)))

    private fun viewModel() = TemplatesViewModel(repository, clock)

    private suspend fun names() = repository.observeAll().first().map { it.name }

    @Test
    fun listsTemplatesAlphabeticallyAfterTheLoadingState() = runTest {
        add("banana", RecordSpec.Text("b"))
        add("Apple", RecordSpec.Text("a"))

        viewModel().uiState.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            assertFalse(state.loading)
            assertEquals(listOf("Apple", "banana"), state.templates.map { it.name })
        }
    }

    @Test
    fun noTemplatesIsAnEmptyState() = runTest {
        viewModel().uiState.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            assertTrue(state.isEmpty)
        }
    }

    // ---- rename ---------------------------------------------------------------------------------------------

    @Test
    fun renamingTrimsTheNameAndTouchesUpdatedAt() = runTest {
        val id = add("old", RecordSpec.Text("x"))

        viewModel().onRename(id, "  new name ")

        val renamed = repository.get(id)!!
        assertEquals("new name", renamed.name)
        assertEquals(Instant.fromEpochMilliseconds(9_000), renamed.updatedAt)
        assertEquals(listOf<RecordSpec>(RecordSpec.Text("x")), renamed.records)
    }

    @Test
    fun renamingToAnExistingNameAddsASuffixButKeepingYourOwnNameIsFine() = runTest {
        add("Oficina", RecordSpec.Text("o"))
        val id = add("Casa", RecordSpec.Text("c"))
        val vm = viewModel()

        vm.onRename(id, "oficina")
        assertEquals("oficina (2)", repository.get(id)!!.name)

        vm.onRename(id, "OFICINA (2)")
        assertEquals("OFICINA (2)", repository.get(id)!!.name, "a template may change the case of its own name")
    }

    @Test
    fun aBlankNameIsIgnored() = runTest {
        val id = add("Casa", RecordSpec.Text("c"))
        viewModel().onRename(id, "   ")
        assertEquals("Casa", repository.get(id)!!.name)
    }

    // ---- delete with undo ---------------------------------------------------------------------------------------

    @Test
    fun deletingRemovesTheTemplateAndUndoBringsItBackWithTheSameId() = runTest {
        val id = add("WiFi casa", RecordSpec.Wifi("Home", "pass1234"))
        val vm = viewModel()

        vm.onDelete(id)
        assertEquals(emptyList(), names())
        assertEquals("WiFi casa", vm.uiState.value.lastDeleted?.name)

        vm.onUndoDelete()
        val restored = repository.get(id)!!
        assertEquals("WiFi casa", restored.name)
        assertEquals(listOf<RecordSpec>(RecordSpec.Wifi("Home", "pass1234")), restored.records)
        assertEquals(null, vm.uiState.value.lastDeleted)
    }

    @Test
    fun undoWithNothingDeletedDoesNothing() = runTest {
        add("keep", RecordSpec.Text("k"))
        viewModel().onUndoDelete()
        assertEquals(listOf("keep"), names())
    }
}
