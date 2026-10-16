package com.tagforge.domain.fakes

import com.tagforge.domain.ports.SettingsStore
import com.tagforge.domain.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsStore(initial: AppSettings = AppSettings()) : SettingsStore {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state

    val current: AppSettings get() = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.update(transform)
    }
}
