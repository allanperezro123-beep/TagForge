package com.tagforge.app.feature.tasks

import app.cash.turbine.test
import com.tagforge.domain.fakes.FixedClock
import com.tagforge.domain.fakes.InMemoryTaskRepository
import com.tagforge.domain.task.RingerMode
import com.tagforge.domain.task.Task
import com.tagforge.domain.task.TaskAction
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** TK-10: list, duplicate, delete with confirmation, run now (routed to the confirmation flow), persistence. */
@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {

    private val repository = InMemoryTaskRepository()
    private val clock = FixedClock(Instant.fromEpochMilliseconds(9_000))

    private val ringer = TaskAction.SetRinger(RingerMode.VIBRATE)
    private val url = TaskAction.OpenUrl("https://example.com")

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun add(name: String, vararg actions: TaskAction): Long =
        repository.upsert(Task(name = name, actions = actions.toList(), createdAt = Instant.fromEpochMilliseconds(1), updatedAt = Instant.fromEpochMilliseconds(1)))

    private fun viewModel() = TasksViewModel(repository, clock)

    private suspend fun names() = repository.observeAll().first().map { it.name }

    // ---- list ---------------------------------------------------------------------------------------------------

    @Test
    fun listsSavedTasksAlphabeticallyAfterTheLoadingState() = runTest {
        add("banana", ringer)
        add("Apple", url)

        viewModel().uiState.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            assertFalse(state.loading)
            assertEquals(listOf("Apple", "banana"), state.tasks.map { it.name })
        }
    }

    @Test
    fun noTasksIsAnEmptyStateNotABlankScreen() = runTest {
        viewModel().uiState.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            assertTrue(state.isEmpty)
        }
    }

    @Test
    fun aSavedTaskIsStillListedWithIdenticalActionsInANewViewModel() = runTest {
        add("Night", ringer, url)

        val reopened = viewModel().uiState.value.tasks

        assertEquals(listOf(ringer, url), reopened.single().actions)
    }

    // ---- duplicate ----------------------------------------------------------------------------------------------

    @Test
    fun duplicatingMakesANewTaskWithTheSameActionsAndAUniqueName() = runTest {
        val id = add("Night", ringer, url)

        viewModel().onDuplicate(id)

        val copies = repository.observeAll().first()
        assertEquals(listOf("Night", "Night (2)"), copies.map { it.name })
        val copy = copies.single { it.id != id }
        assertEquals(listOf(ringer, url), copy.actions)
        assertEquals(Instant.fromEpochMilliseconds(9_000), copy.createdAt)
        assertNull(copy.lastRunAt)
    }

    @Test
    fun duplicatingTwiceKeepsCountingUp() = runTest {
        val id = add("Night", ringer)
        val vm = viewModel()

        vm.onDuplicate(id)
        vm.onDuplicate(id)

        assertEquals(listOf("Night", "Night (2)", "Night (3)"), names())
    }

    @Test
    fun duplicatingAMissingTaskDoesNothing() = runTest {
        viewModel().onDuplicate(404)

        assertEquals(emptyList(), names())
    }

    // ---- delete with confirmation -------------------------------------------------------------------------------------

    @Test
    fun deleteNeedsAConfirmationAndNothingIsRemovedBeforeIt() = runTest {
        val id = add("Night", ringer)
        val vm = viewModel()

        vm.onDeleteRequested(id)

        assertEquals("Night", vm.uiState.value.deleteCandidate?.name)
        assertEquals(listOf("Night"), names())
    }

    @Test
    fun cancellingTheConfirmationKeepsTheTask() = runTest {
        val id = add("Night", ringer)
        val vm = viewModel()
        vm.onDeleteRequested(id)

        vm.onDeleteCancelled()

        assertNull(vm.uiState.value.deleteCandidate)
        assertEquals(listOf("Night"), names())
    }

    @Test
    fun confirmingTheDeletionRemovesOnlyThatTask() = runTest {
        val night = add("Night", ringer)
        add("Day", url)
        val vm = viewModel()
        vm.onDeleteRequested(night)

        vm.onDeleteConfirmed()

        assertEquals(listOf("Day"), names())
        assertNull(vm.uiState.value.deleteCandidate)
    }

    @Test
    fun confirmingWithoutARequestDeletesNothing() = runTest {
        add("Night", ringer)

        viewModel().onDeleteConfirmed()

        assertEquals(listOf("Night"), names())
    }

    // ---- run now ---------------------------------------------------------------------------------------------------------

    @Test
    fun runNowAsksTheRunnerToOpenThatTaskWithoutRunningAnything() = runTest {
        val id = add("Night", ringer)
        val vm = viewModel()

        vm.events.test {
            vm.onRunNow(id)

            assertEquals(TasksEvent.OpenRunner(id), awaitItem())
        }
        assertNull(repository.get(id)!!.lastRunAt, "the runner records the run after the user confirms, not this screen")
    }
}
