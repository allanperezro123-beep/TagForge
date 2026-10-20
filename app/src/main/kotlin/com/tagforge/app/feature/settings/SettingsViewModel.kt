package com.tagforge.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.domain.ports.SettingsStore
import com.tagforge.domain.settings.AppSettings
import com.tagforge.domain.settings.TaskExecutionPolicy
import com.tagforge.domain.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    /** The "run every task without asking" warning is open; the setting only changes once it is accepted. */
    val riskDialogVisible: Boolean = false,
) {
    /** The raw console is only reachable in Advanced mode (Phase 6 renders the entry). */
    val showRawConsoleEntry: Boolean get() = settings.advancedMode
}

class SettingsViewModel(private val store: SettingsStore) : ViewModel() {

    private val riskDialog = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(store.settings, riskDialog) { settings, dialog -> SettingsUiState(settings, dialog) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun onThemeSelected(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun onDynamicColorChanged(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    fun onHapticsChanged(enabled: Boolean) = update { it.copy(hapticsEnabled = enabled) }

    fun onAdvancedChanged(enabled: Boolean) = update { it.copy(advancedMode = enabled) }

    fun onSaveHistoryChanged(enabled: Boolean) = update { it.copy(saveHistory = enabled) }

    fun onConfirmOverwriteChanged(enabled: Boolean) = update { it.copy(confirmOverwrite = enabled) }

    fun onConfirmEraseFormatChanged(enabled: Boolean) = update { it.copy(confirmEraseFormat = enabled) }

    /** "Never ask" is gated by a warning dialog: choosing it only opens the dialog, [onRiskAccepted] applies it. */
    fun onTaskExecutionSelected(policy: TaskExecutionPolicy) {
        if (policy == TaskExecutionPolicy.NEVER_ASK) {
            viewModelScope.launch { if (store.settings.first().taskExecution != policy) riskDialog.value = true }
        } else {
            riskDialog.value = false
            update { it.copy(taskExecution = policy) }
        }
    }

    fun onRiskAccepted() {
        riskDialog.value = false
        update { it.copy(taskExecution = TaskExecutionPolicy.NEVER_ASK) }
    }

    fun onRiskDismissed() {
        riskDialog.value = false
    }

    fun onForgetTrustedTasks() = update { it.copy(trustedTaskFingerprints = emptySet()) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { store.update(transform) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
