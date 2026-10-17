package com.tagforge.app.feature.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.domain.ports.TemplateRepository
import com.tagforge.domain.template.Template
import com.tagforge.domain.template.TemplateNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class TemplatesUiState(
    val loading: Boolean = true,
    val templates: List<Template> = emptyList(),
    val lastDeleted: Template? = null,
) {
    val isEmpty: Boolean get() = !loading && templates.isEmpty()
}

class TemplatesViewModel(
    private val repository: TemplateRepository,
    private val clock: Clock,
) : ViewModel() {

    private val lastDeleted = MutableStateFlow<Template?>(null)

    val uiState: StateFlow<TemplatesUiState> = combine(repository.observeAll(), lastDeleted) { templates, deleted ->
        TemplatesUiState(loading = false, templates = templates, lastDeleted = deleted)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TemplatesUiState())

    /** Blank names are ignored; a name another template already uses gets a numbered suffix. */
    fun onRename(id: Long, requestedName: String) {
        viewModelScope.launch {
            val all = repository.observeAll().first()
            val target = all.firstOrNull { it.id == id } ?: return@launch
            val taken = all.filter { it.id != id }.map { it.name }
            val name = TemplateNames.resolve(requestedName, taken) ?: return@launch
            repository.upsert(target.copy(name = name, updatedAt = clock.now()))
        }
    }

    /** Reversible: deletes at once and the screen offers Undo. */
    fun onDelete(id: Long) {
        viewModelScope.launch {
            val template = repository.get(id) ?: return@launch
            repository.delete(id)
            lastDeleted.update { template }
        }
    }

    fun onUndoDelete() {
        val template = lastDeleted.value ?: return
        lastDeleted.update { null }
        viewModelScope.launch { repository.upsert(template) }
    }
}
