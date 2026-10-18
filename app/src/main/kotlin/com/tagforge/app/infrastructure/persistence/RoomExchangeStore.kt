package com.tagforge.app.infrastructure.persistence

import androidx.room.withTransaction
import com.tagforge.domain.exchange.ExchangeData
import com.tagforge.domain.history.HistoryQuery
import com.tagforge.domain.ports.ExchangeStore
import kotlinx.coroutines.flow.first

/**
 * Backup source and import target. [addMissing] runs inside ONE Room transaction, so a failure anywhere (disk full,
 * constraint) rolls back every row it added: an import is all or nothing (HT-04).
 */
class RoomExchangeStore(private val db: TagForgeDatabase) : ExchangeStore {

    override suspend fun snapshot(): ExchangeData = ExchangeData(
        history = RoomHistoryRepository(db.historyDao()).observe(HistoryQuery(limit = Int.MAX_VALUE)).first(),
        templates = RoomTemplateRepository(db.templateDao()).observeAll().first(),
        tasks = RoomTaskRepository(db.taskDao()).observeAll().first(),
    )

    override suspend fun addMissing(data: ExchangeData): ExchangeData = db.withTransaction {
        val knownHistory = db.historyDao().ids().toSet()
        val knownTemplates = db.templateDao().ids().toSet()
        val knownTasks = db.taskDao().ids().toSet()

        val history = data.history.filter { it.id !in knownHistory }
        val templates = data.templates.filter { it.id !in knownTemplates }
        val tasks = data.tasks.filter { it.id !in knownTasks }

        db.historyDao().insertAll(history.map { it.toEntity() })
        db.templateDao().insertAll(templates.map { it.toEntity() })
        db.taskDao().insertAll(tasks.map { it.toEntity() })
        ExchangeData(history, templates, tasks)
    }
}
