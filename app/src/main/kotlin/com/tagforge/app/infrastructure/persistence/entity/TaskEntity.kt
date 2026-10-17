package com.tagforge.app.infrastructure.persistence.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Created in schema v1 (Phase 2) so Phase 4 needed no migration. [actionsJson] is a JSON list of task actions, not a binary
 * blob (reconciliation #7), so rows stay inspectable and forward-compatible.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "actions_json") val actionsJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_run_at") val lastRunAt: Long?,
)
