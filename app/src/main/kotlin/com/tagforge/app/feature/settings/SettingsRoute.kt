package com.tagforge.app.feature.settings

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagforge.app.di.tagForgeViewModel

/** Container: owns the [SettingsViewModel] and hands plain state and lambdas to [SettingsScreen]. */
@Composable
fun SettingsRoute(onBack: () -> Unit) {
    val viewModel = tagForgeViewModel { SettingsViewModel(it.settingsStore) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        onBack = onBack,
        onThemeSelected = viewModel::onThemeSelected,
        onDynamicColorChanged = viewModel::onDynamicColorChanged,
        onHapticsChanged = viewModel::onHapticsChanged,
        onSaveHistoryChanged = viewModel::onSaveHistoryChanged,
        onAdvancedChanged = viewModel::onAdvancedChanged,
    )
}
