package com.tagforge.app.feature.write.forms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.domain.ndef.spec.LanguageCodes
import java.util.Locale

private val ListMaxHeight = 480.dp

/**
 * Pick the language of a Text record instead of typing its code. "Phone language" comes first, then the common languages with
 * their own name and the name in the phone's language; the search box reaches every other language. The code is what is stored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSheet(selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val display = LocalConfiguration.current.locales.let { if (it.isEmpty) Locale.ROOT else it[0] }
    var query by rememberSaveable { mutableStateOf("") }
    val options = remember(display, query) { LanguageOptions.search(query, display) }
    val deviceCode = remember { LanguageCodes.fromDeviceTag(Locale.getDefault().toLanguageTag()) }
    val device = remember(deviceCode, display) { LanguageOptions.describe(deviceCode, display) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.lang_picker_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.lang_picker_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(Modifier.heightIn(max = ListMaxHeight)) {
                if (query.isBlank()) {
                    item(key = "device") {
                        LanguageRow(
                            title = stringResource(R.string.lang_picker_device),
                            detail = "${device.localizedName} · ${device.code}",
                            checked = selected == device.code,
                            onClick = { onPick(device.code) },
                        )
                    }
                }
                items(options, key = { it.code }) { option ->
                    LanguageRow(
                        title = option.localizedName,
                        detail = if (option.nativeName.equals(option.localizedName, ignoreCase = true)) option.code else "${option.nativeName} · ${option.code}",
                        checked = selected == option.code,
                        onClick = { onPick(option.code) },
                    )
                }
                if (options.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.lang_picker_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun LanguageRow(title: String, detail: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (checked) Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}
