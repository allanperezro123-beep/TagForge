package com.tagforge.app.infrastructure.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tagforge.app.infrastructure.persistence.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    /**
     * Search and filters of HT-03 in one statement. "No filter" is encoded in the arguments (see `HistoryQueryArgs`):
     * `text`/`uid` empty, `allKinds` true, `outcome` 0, `from`/`to` at the Long extremes. `\` is the LIKE escape.
     * `outcome`: 0 any, 1 success only, 2 failed only.
     */
    @Query(
        """
        SELECT * FROM history
        WHERE (:text = ''
               OR summary LIKE '%' || :text || '%' ESCAPE '\'
               OR family LIKE '%' || :text || '%' ESCAPE '\'
               OR (:uid != '' AND uid_hex LIKE '%' || :uid || '%' ESCAPE '\'))
          AND (:allKinds = 1 OR kind IN (:kinds))
          AND (:outcome = 0 OR (:outcome = 1 AND success = 1) OR (:outcome = 2 AND success = 0))
          AND at BETWEEN :from AND :to
        ORDER BY at DESC, id DESC
        LIMIT :limit
        """,
    )
    fun observe(
        text: String,
        uid: String,
        allKinds: Boolean,
        kinds: List<String>,
        outcome: Int,
        from: Long,
        to: Long,
        limit: Int,
    ): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun get(id: Long): HistoryEntity?

    @Insert
    suspend fun insert(entity: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM history")
    suspend fun clear()
}
