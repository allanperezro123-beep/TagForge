package com.tagforge.app.infrastructure.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tagforge.app.infrastructure.persistence.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/** Wired to a repository in Phase 4; declared now so the v1 schema is complete. */
@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Upsert
    suspend fun upsert(entity: TaskEntity): Long

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE tasks SET last_run_at = :at WHERE id = :id")
    suspend fun markRun(id: Long, at: Long)
}
