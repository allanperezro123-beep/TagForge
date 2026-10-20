package com.tagforge.app.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tagforge.app.R

/**
 * The "which app?" control of the Write wizard and the task builder: the chosen app (icon and name) with a button that opens
 * [AppPickerSheet]. Typing the package name by hand is tucked under "Advanced" ([manualEntry] is that text field), and it
 * opens by itself while [errorText] is showing so a problem is never hidden.
 */
@Composable
fun AppPickerField(
    packageName: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    manualEntry: @Composable () -> Unit,
) {
    val packageManager = LocalContext.current.packageManager
    val label = remember(packageName) { if (packageName.isBlank()) null else appLabelOf(packageManager, packageName) }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showManual by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (packageName.isNotBlank()) AppIcon(packageName)
                Column(Modifier.weight(1f)) {
                    Text(
                        label ?: packageName.ifBlank { stringResource(R.string.app_picker_none) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (label != null) {
                        Text(
                            packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                OutlinedButton(onClick = { showSheet = true }) {
                    Text(stringResource(if (packageName.isBlank()) R.string.app_picker_choose else R.string.app_picker_change))
                }
            }
        }
        Text(stringResource(R.string.app_picker_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (errorText != null) Text(errorText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { showManual = !showManual }) {
            Text(stringResource(if (showManual) R.string.app_picker_advanced_hide else R.string.app_picker_advanced))
        }
        if (showManual || errorText != null) manualEntry()
    }

    if (showSheet) {
        AppPickerSheet(
            onPick = {
                showSheet = false
                onPick(it.packageName)
            },
            onDismiss = { showSheet = false },
        )
    }
}
