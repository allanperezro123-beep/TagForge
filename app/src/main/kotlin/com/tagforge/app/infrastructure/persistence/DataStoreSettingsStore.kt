package com.tagforge.app.infrastructure.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tagforge.domain.ports.SettingsStore
import com.tagforge.domain.settings.AppSettings
import com.tagforge.domain.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Settings in a Preferences DataStore. Keys are never renamed; new keys always read with a default so an
 * existing file keeps working.
 */
class DataStoreSettingsStore(private val dataStore: DataStore<Preferences>) : SettingsStore {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { preferences ->
            val updated = transform(preferences.toSettings())
            preferences[THEME_MODE] = updated.themeMode.code
            preferences[DYNAMIC_COLOR] = updated.dynamicColor
            preferences[HAPTICS] = updated.hapticsEnabled
            preferences[ADVANCED_MODE] = updated.advancedMode
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = ThemeMode.fromCode(this[THEME_MODE]),
            dynamicColor = this[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            hapticsEnabled = this[HAPTICS] ?: defaults.hapticsEnabled,
            advancedMode = this[ADVANCED_MODE] ?: defaults.advancedMode,
        )
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HAPTICS = booleanPreferencesKey("haptics")
        val ADVANCED_MODE = booleanPreferencesKey("advanced_mode")
    }
}
