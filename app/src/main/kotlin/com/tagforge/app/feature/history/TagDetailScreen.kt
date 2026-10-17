package com.tagforge.app.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagforge.app.R
import com.tagforge.app.di.tagForgeViewModel
import com.tagforge.app.ui.components.BackNavigationIcon
import com.tagforge.app.ui.components.EmptyState
import com.tagforge.app.ui.components.RecordCard
import com.tagforge.app.ui.components.SkeletonList
import com.tagforge.domain.history.HistoryEntry

/** Container: loads one stored entry by id. */
@Composable
fun TagDetailRoute(historyId: Long, onBack: () -> Unit) {
    val viewModel = tagForgeViewModel { TagDetailViewModel(historyId, it.historyRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TagDetailScreen(state, onBack)
}

/** A stored history entry shown like a fresh read: what happened, to which tag, and the records it held. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagDetailScreen(state: TagDetailUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_detail_title)) },
                navigationIcon = { BackNavigationIcon(onBack) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                TagDetailUiState.Loading -> SkeletonList(Modifier.padding(16.dp))
                TagDetailUiState.NotFound -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.history_detail_not_found_title),
                    body = stringResource(R.string.history_detail_not_found_body),
                )
                is TagDetailUiState.Loaded -> LoadedDetail(state)
            }
        }
    }
}

@Composable
private fun LoadedDetail(state: TagDetailUiState.Loaded) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { EntryHeader(state.entry) }
        when {
            state.unreadable -> item {
                Text(stringResource(R.string.history_detail_unreadable), color = MaterialTheme.colorScheme.error)
            }
            state.records.isEmpty() -> item {
                Text(stringResource(R.string.history_detail_no_records), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        itemsIndexed(state.records) { index, parsed -> RecordCard(parsed, index) }
    }
}

@Composable
private fun EntryHeader(entry: HistoryEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailLine(stringResource(R.string.history_detail_action), stringResource(entry.kind.labelRes()))
            DetailLine(stringResource(R.string.history_detail_result), stringResource(entry.outcome.labelRes()))
            DetailLine(stringResource(R.string.history_detail_when), rememberDateText(entry.at))
            entry.uid?.let { DetailLine(stringResource(R.string.info_uid), it.toHex(":")) }
            entry.tagType?.let { DetailLine(stringResource(R.string.info_type), it) }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
