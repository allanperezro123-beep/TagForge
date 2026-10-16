package com.tagforge.domain.ports

import com.tagforge.domain.settings.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsStore {
    val settings: Flow<AppSettings>

    /** Atomically applies [transform] to the current settings and persists the result. */
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
