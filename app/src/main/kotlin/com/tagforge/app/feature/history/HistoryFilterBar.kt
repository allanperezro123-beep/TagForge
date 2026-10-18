package com.tagforge.app.feature.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.OutcomeFilter

/**
 * Search field plus filter chips (kind, outcome, date range). Typing filters after a short pause: there is no
 * submit button. A selected chip shows a check mark as well as its colour, so state is never colour alone.
 */
@Composable
fun HistoryFilterBar(
    filters: HistoryFilters,
    onSearchChanged: (String) -> Unit,
    onToggleKind: (HistoryKind) -> Unit,
    onOutcomeChanged: (OutcomeFilter) -> Unit,
    onRangeChanged: (DateRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = filters.text,
            onValueChange = onSearchChanged,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            label = { Text(stringResource(R.string.history_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (filters.text.isNotEmpty()) {
                    IconButton(onClick = { onSearchChanged("") }) {
                        Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.history_search_clear))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        ChipRow {
            HistoryKind.entries.forEach { kind ->
                SelectableChip(
                    label = stringResource(kind.labelRes()),
                    selected = kind in filters.kinds,
                    onClick = { onToggleKind(kind) },
                )
            }
        }
        ChipRow {
            OutcomeFilter.entries.forEach { outcome ->
                SelectableChip(
                    label = stringResource(outcome.labelRes()),
                    selected = filters.outcome == outcome,
                    onClick = { onOutcomeChanged(outcome) },
                )
            }
        }
        ChipRow {
            DateRange.entries.forEach { range ->
                SelectableChip(
                    label = stringResource(range.labelRes()),
                    selected = filters.range == range,
                    onClick = { onRangeChanged(range) },
                )
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Outlined.Done, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
    )
}

private fun OutcomeFilter.labelRes(): Int = when (this) {
    OutcomeFilter.ALL -> R.string.history_filter_outcome_all
    OutcomeFilter.SUCCESS -> R.string.history_outcome_success
    OutcomeFilter.FAILED -> R.string.history_filter_outcome_failed
}

private fun DateRange.labelRes(): Int = when (this) {
    DateRange.ANY -> R.string.history_filter_range_any
    DateRange.DAY -> R.string.history_filter_range_day
    DateRange.WEEK -> R.string.history_filter_range_week
    DateRange.MONTH -> R.string.history_filter_range_month
}
