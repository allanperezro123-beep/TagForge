package com.tagforge.app.infrastructure.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.tagforge.app.infrastructure.persistence.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/** Backs [com.tagforge.app.infrastructure.persistence.RoomTaskRepository]. */
@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Upsert
    suspend fun upsert(entity: TaskEntity): Long

    @Query("SELECT id FROM tasks")
    suspend fun ids(): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE tasks SET last_run_at = :at WHERE id = :id")
    suspend fun markRun(id: Long, at: Long)
}
