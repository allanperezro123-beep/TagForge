package com.tagforge.app.infrastructure.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tagforge.app.infrastructure.persistence.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY at DESC, id DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun get(id: Long): HistoryEntity?

    @Insert
    suspend fun insert(entity: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM history")
    suspend fun clear()
}
