package com.tagforge.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.history.HistoryEntry
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.ParsedRecord
import com.tagforge.domain.ndef.RecordInterpreter
import com.tagforge.domain.ports.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TagDetailUiState {
    data object Loading : TagDetailUiState
    data object NotFound : TagDetailUiState

    /** [unreadable] is true when stored bytes exist but no longer parse; the entry's summary is still shown. */
    data class Loaded(val entry: HistoryEntry, val records: List<ParsedRecord>, val unreadable: Boolean) : TagDetailUiState
}

/** Renders a stored history entry like a fresh read. */
class TagDetailViewModel(historyId: Long, history: HistoryRepository) : ViewModel() {

    private val _state = MutableStateFlow<TagDetailUiState>(TagDetailUiState.Loading)
    val uiState: StateFlow<TagDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val entry = history.get(historyId)
            _state.value = if (entry == null) TagDetailUiState.NotFound else load(entry)
        }
    }

    private fun load(entry: HistoryEntry): TagDetailUiState {
        val bytes = entry.ndef ?: return TagDetailUiState.Loaded(entry, emptyList(), unreadable = false)
        return when (val decoded = DefaultNdefCodec.decode(bytes)) {
            is Outcome.Ok -> TagDetailUiState.Loaded(entry, RecordInterpreter.interpret(decoded.value), unreadable = false)
            is Outcome.Err -> TagDetailUiState.Loaded(entry, emptyList(), unreadable = true)
        }
    }
}
