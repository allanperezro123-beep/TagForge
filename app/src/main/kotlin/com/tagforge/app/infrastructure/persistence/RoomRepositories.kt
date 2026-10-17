package com.tagforge.app.infrastructure.persistence

import com.tagforge.app.infrastructure.persistence.dao.HistoryDao
import com.tagforge.app.infrastructure.persistence.dao.TaskDao
import com.tagforge.app.infrastructure.persistence.dao.TemplateDao
import com.tagforge.domain.history.HistoryEntry
import com.tagforge.domain.history.HistoryQuery
import com.tagforge.domain.ports.HistoryRepository
import com.tagforge.domain.ports.TaskRepository
import com.tagforge.domain.ports.TemplateRepository
import com.tagforge.domain.task.Task
import com.tagforge.domain.template.Template
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomHistoryRepository(private val dao: HistoryDao) : HistoryRepository {
    override fun observe(query: HistoryQuery): Flow<List<HistoryEntry>> =
        dao.observe(query.limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun add(entry: HistoryEntry): Long = dao.insert(entry.toEntity())

    override suspend fun get(id: Long): HistoryEntry? = dao.get(id)?.toDomain()

    override suspend fun delete(ids: Set<Long>) = dao.delete(ids.toList())

    override suspend fun clear() = dao.clear()
}

class RoomTemplateRepository(private val dao: TemplateDao) : TemplateRepository {
    override fun observeAll(): Flow<List<Template>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: Long): Template? = dao.get(id)?.toDomain()

    override suspend fun upsert(template: Template): Long {
        val inserted = dao.upsert(template.toEntity())
        // Room returns the new row id for an insert and -1 for an update of an existing id
        return if (inserted == -1L) template.id else inserted
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}

class RoomTaskRepository(private val dao: TaskDao) : TaskRepository {
    override fun observeAll(): Flow<List<Task>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: Long): Task? = dao.get(id)?.toDomain()

    override suspend fun upsert(task: Task): Long {
        val inserted = dao.upsert(task.toEntity())
        // Room returns the new row id for an insert and -1 for an update of an existing id
        return if (inserted == -1L) task.id else inserted
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun markRun(id: Long, at: kotlin.time.Instant) = dao.markRun(id, at.toEpochMilliseconds())
}
