package com.tagforge.app.infrastructure.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.settings.AppSettings
import com.tagforge.domain.settings.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AS-03 "survives restart" and AS-07 "Default".
 *
 * Most tests use an in-memory [DataStore] because the real file-backed one renames `x.tmp` over an existing `x`,
 * which fails on Windows JVMs from the second write onwards (a platform limitation of DataStore on desktop
 * Windows, irrelevant on Android). One test still goes through a real file with a single write to prove persistence.
 */
class DataStoreSettingsStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class InMemoryPreferences : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    @Test
    fun freshInstallUsesTheDocumentedDefaults() = runBlocking {
        val settings = DataStoreSettingsStore(InMemoryPreferences()).settings.first()

        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(true, settings.hapticsEnabled)
        assertEquals(false, settings.advancedMode)
        assertEquals(true, settings.dynamicColor)
        assertEquals(AppSettings(), settings)
    }

    @Test
    fun updatesAreVisibleImmediately() = runBlocking {
        val store = DataStoreSettingsStore(InMemoryPreferences())

        store.update { it.copy(themeMode = ThemeMode.DARK) }
        assertEquals(ThemeMode.DARK, store.settings.first().themeMode)

        store.update { it.copy(hapticsEnabled = false, advancedMode = true) }
        assertEquals(
            AppSettings(themeMode = ThemeMode.DARK, hapticsEnabled = false, advancedMode = true),
            store.settings.first(),
        )
    }

    @Test
    fun aNewStoreOverTheSameDataSeesThePreviousValues() = runBlocking {
        val data = InMemoryPreferences()
        DataStoreSettingsStore(data).update {
            it.copy(themeMode = ThemeMode.LIGHT, dynamicColor = false, hapticsEnabled = false, advancedMode = true)
        }

        assertEquals(
            AppSettings(themeMode = ThemeMode.LIGHT, dynamicColor = false, hapticsEnabled = false, advancedMode = true),
            DataStoreSettingsStore(data).settings.first(),
        )
    }

    @Test
    fun transformReceivesTheCurrentPersistedValue() = runBlocking {
        val store = DataStoreSettingsStore(InMemoryPreferences())
        store.update { it.copy(themeMode = ThemeMode.DARK) }

        var seen: AppSettings? = null
        store.update { current -> current.also { seen = it } }

        assertEquals(ThemeMode.DARK, seen?.themeMode)
    }

    @Test
    fun unknownStoredThemeCodeFallsBackToSystem() = runBlocking {
        val data = InMemoryPreferences()
        data.edit { it[stringPreferencesKey("theme_mode")] = "sepia" }

        assertEquals(ThemeMode.SYSTEM, DataStoreSettingsStore(data).settings.first().themeMode)
    }

    @Test
    fun saveHistoryDefaultsOnAndTheLastTargetChipDefaultsToNtag213() = runBlocking {
        val settings = DataStoreSettingsStore(InMemoryPreferences()).settings.first()
        assertEquals(true, settings.saveHistory)
        assertEquals(NtagModel.NTAG213, settings.lastTargetModel)
    }

    @Test
    fun saveHistoryAndLastTargetPersistAcrossStores() = runBlocking {
        val data = InMemoryPreferences()
        DataStoreSettingsStore(data).update { it.copy(saveHistory = false, lastTargetModel = NtagModel.NTAG216) }

        val reopened = DataStoreSettingsStore(data).settings.first()

        assertEquals(false, reopened.saveHistory)
        assertEquals(NtagModel.NTAG216, reopened.lastTargetModel)
    }

    @Test
    fun theBatchCounterStartsAtOneAndSurvivesRecreatingTheStore() = runBlocking {
        val data = InMemoryPreferences()
        assertEquals(1L, DataStoreSettingsStore(data).settings.first().batchNextCounter)

        DataStoreSettingsStore(data).update { it.copy(batchNextCounter = 9L) }

        assertEquals(9L, DataStoreSettingsStore(data).settings.first().batchNextCounter)
        // another setting changing never disturbs the counter
        DataStoreSettingsStore(data).update { it.copy(hapticsEnabled = false) }
        assertEquals(9L, DataStoreSettingsStore(data).settings.first().batchNextCounter)
    }

    @Test
    fun anUnknownStoredTargetChipFallsBackToNtag213() = runBlocking {
        val data = InMemoryPreferences()
        data.edit { it[stringPreferencesKey("last_target_model")] = "NTAG999" }

        assertEquals(NtagModel.NTAG213, DataStoreSettingsStore(data).settings.first().lastTargetModel)
    }

    @Test
    fun valuesSurviveReopeningTheRealFile() = runBlocking {
        val file = File(folder.root, "settings.preferences_pb")
        val firstJob = Job()
        val first = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + firstJob)) { file }
        DataStoreSettingsStore(first).update { it.copy(themeMode = ThemeMode.DARK, hapticsEnabled = false) }
        firstJob.cancelAndJoin()   // "process death"

        val secondJob = Job()
        val second = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + secondJob)) { file }
        val reopened = DataStoreSettingsStore(second).settings.first()
        secondJob.cancelAndJoin()

        assertEquals(AppSettings(themeMode = ThemeMode.DARK, hapticsEnabled = false), reopened)
    }
}
