package com.tagforge.domain.ports

import com.tagforge.domain.task.Task
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface TaskRepository {
    /** Alphabetical, case-insensitive. Emits again whenever the data changes. */
    fun observeAll(): Flow<List<Task>>

    suspend fun get(id: Long): Task?

    /** Inserts when [Task.id] is 0, otherwise replaces. Returns the id. */
    suspend fun upsert(task: Task): Long

    suspend fun delete(id: Long)

    suspend fun markRun(id: Long, at: Instant)
}
