package com.tagforge.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.components.BackNavigationIcon
import com.tagforge.domain.settings.ThemeMode

/** Stateless settings screen: renders [SettingsUiState] and reports user intent through lambdas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    dynamicColorSupported: Boolean,
    onBack: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onSaveHistoryChanged: (Boolean) -> Unit,
    onAdvancedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { BackNavigationIcon(onBack) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.settings_section_appearance))
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    ThemeOption(mode, selected = state.settings.themeMode == mode, onSelect = { onThemeSelected(mode) })
                }
            }
            if (dynamicColorSupported) {
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    description = stringResource(R.string.settings_dynamic_color_desc),
                    checked = state.settings.dynamicColor,
                    onCheckedChange = onDynamicColorChanged,
                )
            }

            SectionHeader(stringResource(R.string.settings_section_feedback))
            SwitchRow(
                title = stringResource(R.string.settings_haptics),
                description = stringResource(R.string.settings_haptics_desc),
                checked = state.settings.hapticsEnabled,
                onCheckedChange = onHapticsChanged,
            )

            SectionHeader(stringResource(R.string.settings_section_history))
            SwitchRow(
                title = stringResource(R.string.settings_save_history),
                description = stringResource(R.string.settings_save_history_desc),
                checked = state.settings.saveHistory,
                onCheckedChange = onSaveHistoryChanged,
            )

            SectionHeader(stringResource(R.string.settings_section_advanced))
            SwitchRow(
                title = stringResource(R.string.settings_advanced),
                description = stringResource(R.string.settings_advanced_desc),
                checked = state.settings.advancedMode,
                onCheckedChange = onAdvancedChanged,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun ThemeOption(mode: ThemeMode, selected: Boolean, onSelect: () -> Unit) {
    val label = stringResource(
        when (mode) {
            ThemeMode.SYSTEM -> R.string.settings_theme_system
            ThemeMode.LIGHT -> R.string.settings_theme_light
            ThemeMode.DARK -> R.string.settings_theme_dark
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}
