package com.tagforge.domain.ports

import com.tagforge.domain.history.HistoryEntry
import com.tagforge.domain.history.HistoryQuery
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    /** Newest first. Emits again whenever the data changes. */
    fun observe(query: HistoryQuery = HistoryQuery()): Flow<List<HistoryEntry>>

    suspend fun add(entry: HistoryEntry): Long

    suspend fun get(id: Long): HistoryEntry?

    suspend fun delete(ids: Set<Long>)

    /** Removes history only; templates are never touched. */
    suspend fun clear()
}
