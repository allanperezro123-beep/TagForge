package com.tagforge.app.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.feature.read.techLabel
import com.tagforge.app.ui.components.EmptyState
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TechSection

/**
 * One block per technology the tag reports (TT-05): technology name as the platform calls it, then label/value rows.
 * Pure presentation of an already-built [TagInfo]; it sends nothing to the tag.
 */
@Composable
fun TechSections(info: TagInfo, modifier: Modifier = Modifier) {
    val sections = info.details.sections(info)
    if (sections.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Memory,
            title = stringResource(R.string.tech_none_title),
            body = stringResource(R.string.tech_none_body),
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(sections, key = { it.tech.name }) { section -> TechSectionBlock(section) }
    }
}

@Composable
private fun TechSectionBlock(section: TechSection) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(techLabel(section.tech), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        section.entries.forEach { entry ->
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(entry.key.labelRes()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer { Text(entry.value.display(entry.key), style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}
