package com.tagforge.app.feature.settings

import app.cash.turbine.test
import com.tagforge.domain.fakes.FakeSettingsStore
import com.tagforge.domain.settings.AppSettings
import com.tagforge.domain.settings.TaskExecutionPolicy
import com.tagforge.domain.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** AS-03 (theme persists) and AS-07 (defaults, Advanced gate). */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val store = FakeSettingsStore()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun freshInstallHasAdvancedOffAndRawConsoleHidden() = runTest {
        SettingsViewModel(store).uiState.test {
            val state = awaitItem()
            assertEquals(ThemeMode.SYSTEM, state.settings.themeMode)
            assertEquals(true, state.settings.hapticsEnabled)
            assertFalse(state.settings.advancedMode)
            assertFalse(state.showRawConsoleEntry)
        }
    }

    @Test
    fun themeChoicePersistsToTheStoreAndIsReflectedImmediately() = runTest {
        val vm = SettingsViewModel(store)
        vm.uiState.test {
            awaitItem()
            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)) {
                vm.onThemeSelected(mode)
                assertEquals(mode, awaitItem().settings.themeMode)
                assertEquals(mode, store.current.themeMode)
            }
        }
    }

    @Test
    fun hapticsToggleIsPersisted() = runTest {
        val vm = SettingsViewModel(store)
        vm.uiState.test {
            awaitItem()
            vm.onHapticsChanged(false)
            assertFalse(awaitItem().settings.hapticsEnabled)
            assertFalse(store.current.hapticsEnabled)
            vm.onHapticsChanged(true)
            assertTrue(awaitItem().settings.hapticsEnabled)
        }
    }

    @Test
    fun enablingAdvancedRevealsTheRawConsoleEntryAndDisablingHidesItAgain() = runTest {
        val vm = SettingsViewModel(store)
        vm.uiState.test {
            assertFalse(awaitItem().showRawConsoleEntry)
            vm.onAdvancedChanged(true)
            assertTrue(awaitItem().showRawConsoleEntry)
            vm.onAdvancedChanged(false)
            assertFalse(awaitItem().showRawConsoleEntry)
        }
    }

    @Test
    fun dynamicColorToggleIsPersisted() = runTest {
        val vm = SettingsViewModel(store)
        vm.uiState.test {
            assertFalse(awaitItem().settings.dynamicColor)
            vm.onDynamicColorChanged(true)
            assertTrue(awaitItem().settings.dynamicColor)
        }
        assertEquals(AppSettings(dynamicColor = true), store.current)
    }

    @Test
    fun changingOneSettingKeepsTheOthers() = runTest {
        val vm = SettingsViewModel(store)
        vm.uiState.test {
            awaitItem()
            vm.onThemeSelected(ThemeMode.DARK)
            awaitItem()
            vm.onAdvancedChanged(true)
            awaitItem()
        }
        assertEquals(AppSettings(themeMode = ThemeMode.DARK, advancedMode = true), store.current)
    }

    @Test
    fun saveHistoryCanBeSwitchedOffAndBackOn() = runTest {
        val vm = SettingsViewModel(store)
        vm.uiState.test {
            assertTrue(awaitItem().settings.saveHistory)
            vm.onSaveHistoryChanged(false)
            assertFalse(awaitItem().settings.saveHistory)
            vm.onSaveHistoryChanged(true)
            assertTrue(awaitItem().settings.saveHistory)
        }
    }

    // ---- UX-A2: the confirmations the user may relax ----------------------------------------------------------------------

    @Test
    fun everyConfirmationStartsOnAndTasksAskAlways() = runTest {
        SettingsViewModel(store).uiState.test {
            val state = awaitItem()
            assertTrue(state.settings.confirmOverwrite)
            assertTrue(state.settings.confirmEraseFormat)
            assertEquals(TaskExecutionPolicy.ASK_ALWAYS, state.settings.taskExecution)
            assertFalse(state.riskDialogVisible)
        }
    }

    @Test
    fun theOverwriteAndEraseTogglesArePersisted() = runTest {
        val vm = SettingsViewModel(store)
        vm.uiState.test {
            awaitItem()
            vm.onConfirmOverwriteChanged(false)
            assertFalse(awaitItem().settings.confirmOverwrite)
            vm.onConfirmEraseFormatChanged(false)
            assertFalse(awaitItem().settings.confirmEraseFormat)
            assertFalse(store.current.confirmOverwrite)
            assertFalse(store.current.confirmEraseFormat)
        }
    }

    @Test
    fun theOwnTasksPolicyAppliesAtOnceWithoutAWarning() = runTest {
        val vm = SettingsViewModel(store)
        vm.uiState.test {
            awaitItem()
            vm.onTaskExecutionSelected(TaskExecutionPolicy.OWN_TASKS)
            val state = awaitItem()
            assertEquals(TaskExecutionPolicy.OWN_TASKS, state.settings.taskExecution)
            assertFalse(state.riskDialogVisible)
        }
    }

    @Test
    fun neverAskOnlyOpensTheWarningAndChangesNothingUntilItIsAccepted() = runTest {
        val vm = SettingsViewModel(store)
        vm.uiState.test {
            awaitItem()
            vm.onTaskExecutionSelected(TaskExecutionPolicy.NEVER_ASK)
            val asked = awaitItem()
            assertTrue(asked.riskDialogVisible)
            assertEquals(TaskExecutionPolicy.ASK_ALWAYS, asked.settings.taskExecution)
            assertEquals(TaskExecutionPolicy.ASK_ALWAYS, store.current.taskExecution)

            vm.onRiskDismissed()
            assertFalse(awaitItem().riskDialogVisible)
            assertEquals(TaskExecutionPolicy.ASK_ALWAYS, store.current.taskExecution, "cancelling keeps asking")
        }
    }

    @Test
    fun acceptingTheWarningTurnsNeverAskOnAndOneTapGoesBack() = runTest {
        val vm = SettingsViewModel(store)
        vm.onTaskExecutionSelected(TaskExecutionPolicy.NEVER_ASK)
        vm.onRiskAccepted()
        assertEquals(TaskExecutionPolicy.NEVER_ASK, store.current.taskExecution)

        vm.onTaskExecutionSelected(TaskExecutionPolicy.ASK_ALWAYS)
        assertEquals(TaskExecutionPolicy.ASK_ALWAYS, store.current.taskExecution)
    }

    @Test
    fun forgettingTrustedTasksEmptiesTheSet() = runTest {
        store.update { it.copy(taskExecution = TaskExecutionPolicy.OWN_TASKS, trustedTaskFingerprints = setOf("aa", "bb")) }
        SettingsViewModel(store).onForgetTrustedTasks()
        assertEquals(emptySet(), store.current.trustedTaskFingerprints)
        assertEquals(TaskExecutionPolicy.OWN_TASKS, store.current.taskExecution)
    }
}
