package com.tagforge.app.infrastructure.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tagforge.app.infrastructure.persistence.entity.TemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun get(id: Long): TemplateEntity?

    @Upsert
    suspend fun upsert(entity: TemplateEntity): Long

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun delete(id: Long)
}
