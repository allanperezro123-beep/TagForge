package com.tagforge.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.domain.history.HistoryEntry
import com.tagforge.domain.ports.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [loading] is true only until the first emission, so the screen can show skeleton rows of the final height and swap
 * them for the list without a layout shift (AS-06).
 */
data class HistoryUiState(
    val loading: Boolean = true,
    val entries: List<HistoryEntry> = emptyList(),
    val confirmClear: Boolean = false,
    val lastDeleted: HistoryEntry? = null,
) {
    val isEmpty: Boolean get() = !loading && entries.isEmpty()
}

class HistoryViewModel(private val history: HistoryRepository) : ViewModel() {

    private data class Transient(val confirmClear: Boolean = false, val lastDeleted: HistoryEntry? = null)

    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<HistoryUiState> = combine(history.observe(), transient) { entries, extra ->
        HistoryUiState(loading = false, entries = entries, confirmClear = extra.confirmClear, lastDeleted = extra.lastDeleted)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HistoryUiState())

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
}
