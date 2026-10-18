package com.tagforge.domain.fakes

import com.tagforge.domain.history.HistoryEntry
import com.tagforge.domain.history.HistoryQuery
import com.tagforge.domain.ports.HistoryRepository
import com.tagforge.domain.ports.TaskRepository
import com.tagforge.domain.ports.TemplateRepository
import com.tagforge.domain.task.Task
import com.tagforge.domain.template.Template
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class InMemoryHistoryRepository : HistoryRepository {
    private val rows = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private var nextId = 1L

    override fun observe(query: HistoryQuery): Flow<List<HistoryEntry>> =
        rows.map { all ->
            all.filter(query::matches)
                .sortedWith(compareByDescending<HistoryEntry> { it.at }.thenByDescending { it.id })
                .take(query.limit)
        }

    override suspend fun add(entry: HistoryEntry): Long {
        val id = if (entry.id != 0L) entry.id else nextId++
        rows.update { it.filterNot { existing -> existing.id == id } + entry.copy(id = id) }
        return id
    }

    override suspend fun get(id: Long): HistoryEntry? = rows.value.firstOrNull { it.id == id }

    override suspend fun delete(ids: Set<Long>) {
        rows.update { list -> list.filterNot { it.id in ids } }
    }

    override suspend fun clear() {
        rows.value = emptyList()
    }
}

class InMemoryTemplateRepository : TemplateRepository {
    private val rows = MutableStateFlow<List<Template>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<Template>> = rows.map { all -> all.sortedBy { it.name.lowercase() } }

    override suspend fun get(id: Long): Template? = rows.value.firstOrNull { it.id == id }

    override suspend fun upsert(template: Template): Long {
        val id = if (template.id != 0L) template.id else nextId++
        rows.update { it.filterNot { existing -> existing.id == id } + template.copy(id = id) }
        return id
    }

    override suspend fun delete(id: Long) {
        rows.update { list -> list.filterNot { it.id == id } }
    }
}

class InMemoryTaskRepository : TaskRepository {
    private val rows = MutableStateFlow<List<Task>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<Task>> = rows.map { all -> all.sortedBy { it.name.lowercase() } }

    override suspend fun get(id: Long): Task? = rows.value.firstOrNull { it.id == id }

    override suspend fun upsert(task: Task): Long {
        val id = if (task.id != 0L) task.id else nextId++
        rows.update { it.filterNot { existing -> existing.id == id } + task.copy(id = id) }
        return id
    }

    override suspend fun delete(id: Long) {
        rows.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun markRun(id: Long, at: kotlin.time.Instant) {
        rows.update { list -> list.map { if (it.id == id) it.copy(lastRunAt = at) else it } }
    }
}
