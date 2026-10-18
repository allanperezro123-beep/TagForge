package com.tagforge.app.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tagforge.app.R

/** The 19 kinds of action, grouped. Choosing one opens its form; the sheet closes with back or an outside tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPickerSheet(
    onSelect: (ActionKind) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        Column {
            Text(
                stringResource(R.string.builder_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
            )
            LazyColumn {
                ActionGroup.entries.forEach { group ->
                    item(key = "group-${group.name}") {
                        Text(
                            stringResource(group.titleRes()),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
                        )
                    }
                    items(ActionKind.entries.filter { it.group == group }, key = { it.name }) { kind ->
                        ListItem(
                            modifier = Modifier.clickable { onSelect(kind) },
                            leadingContent = { Icon(kind.icon(), contentDescription = null) },
                            headlineContent = { Text(stringResource(kind.titleRes())) },
                        )
                    }
                }
            }
        }
    }
}
