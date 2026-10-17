package com.tagforge.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.domain.ports.SettingsStore
import com.tagforge.domain.settings.AppSettings
import com.tagforge.domain.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
) {
    /** The raw console is only reachable in Advanced mode (Phase 6 renders the entry). */
    val showRawConsoleEntry: Boolean get() = settings.advancedMode
}

class SettingsViewModel(private val store: SettingsStore) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = store.settings
        .map(::SettingsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun onThemeSelected(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun onDynamicColorChanged(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    fun onHapticsChanged(enabled: Boolean) = update { it.copy(hapticsEnabled = enabled) }

    fun onAdvancedChanged(enabled: Boolean) = update { it.copy(advancedMode = enabled) }

    fun onSaveHistoryChanged(enabled: Boolean) = update { it.copy(saveHistory = enabled) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { store.update(transform) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
