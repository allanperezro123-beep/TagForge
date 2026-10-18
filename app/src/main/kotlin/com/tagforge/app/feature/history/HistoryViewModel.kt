package com.tagforge.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.domain.history.HistoryEntry
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryQuery
import com.tagforge.domain.history.OutcomeFilter
import com.tagforge.domain.ports.HistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/** How far back the date chip looks, counted from the moment the query is built. */
enum class DateRange(val windowMillis: Long?) {
    ANY(null),
    DAY(24.hours.inWholeMilliseconds),
    WEEK(7.days.inWholeMilliseconds),
    MONTH(30.days.inWholeMilliseconds),
}

/** What the search field and the chips show right now. Applied to the list at once, except [text] (debounced). */
data class HistoryFilters(
    val text: String = "",
    val kinds: Set<HistoryKind> = emptySet(),
    val outcome: OutcomeFilter = OutcomeFilter.ALL,
    val range: DateRange = DateRange.ANY,
) {
    val isActive: Boolean
        get() = text.isNotBlank() || kinds.isNotEmpty() || outcome != OutcomeFilter.ALL || range != DateRange.ANY
}

/**
 * [loading] is true only until the first emission, so the screen can show skeleton rows of the final height and swap
 * them for the list without a layout shift (AS-06). [isEmpty] means there is no history at all; [noResults] means
 * entries exist but the current search or filters match none of them.
 */
data class HistoryUiState(
    val loading: Boolean = true,
    val entries: List<HistoryEntry> = emptyList(),
    val filters: HistoryFilters = HistoryFilters(),
    val confirmClear: Boolean = false,
    val lastDeleted: HistoryEntry? = null,
) {
    val isEmpty: Boolean get() = !loading && entries.isEmpty() && !filters.isActive
    val noResults: Boolean get() = !loading && entries.isEmpty() && filters.isActive
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val history: HistoryRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private data class Transient(val confirmClear: Boolean = false, val lastDeleted: HistoryEntry? = null)

    private val transient = MutableStateFlow(Transient())
    private val filters = MutableStateFlow(HistoryFilters())

    /** Text waits for a pause in typing (no Enter needed); clearing it applies at once. The chips apply immediately. */
    private val debouncedText: Flow<String> = filters.map { it.text }
        .distinctUntilChanged()
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }

    private val chips = filters.map { Triple(it.kinds, it.outcome, it.range) }.distinctUntilChanged()

    private val query: Flow<HistoryQuery> = combine(debouncedText, chips) { text, (kinds, outcome, range) ->
        HistoryQuery(
            text = text,
            kinds = kinds,
            outcome = outcome,
            from = range.windowMillis?.let { clock.now() - it.milliseconds },
        )
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        query.flatMapLatest { history.observe(it) },
        filters,
        transient,
    ) { entries, active, extra ->
        HistoryUiState(
            loading = false,
            entries = entries,
            filters = active,
            confirmClear = extra.confirmClear,
            lastDeleted = extra.lastDeleted,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HistoryUiState())

    // ---- search and filters (HT-03) ----------------------------------------------------------------------------

    fun onSearchChanged(text: String) = filters.update { it.copy(text = text) }

    fun onToggleKind(kind: HistoryKind) =
        filters.update { it.copy(kinds = if (kind in it.kinds) it.kinds - kind else it.kinds + kind) }

    fun onOutcomeChanged(outcome: OutcomeFilter) = filters.update { it.copy(outcome = outcome) }

    fun onRangeChanged(range: DateRange) = filters.update { it.copy(range = range) }

    fun onClearFilters() {
        filters.value = HistoryFilters()
    }

    // ---- clearing and deleting ---------------------------------------------------------------------------------

    /** Clearing is irreversible, so it always goes through a confirmation first (HT-05). */
    fun onClearRequested() = transient.update { it.copy(confirmClear = true) }

    fun onClearDismissed() = transient.update { it.copy(confirmClear = false) }

    fun onClearConfirmed() {
        transient.update { it.copy(confirmClear = false, lastDeleted = null) }
        viewModelScope.launch { history.clear() }
    }

    /** Deleting one entry is reversible: it happens at once and the screen offers Undo. */
    fun onDelete(id: Long) {
        viewModelScope.launch {
            val entry = history.get(id) ?: return@launch
            history.delete(setOf(id))
            transient.update { it.copy(lastDeleted = entry) }
        }
    }

    fun onUndoDelete() {
        val entry = transient.value.lastDeleted ?: return
        transient.update { it.copy(lastDeleted = null) }
        viewModelScope.launch { history.add(entry) }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
