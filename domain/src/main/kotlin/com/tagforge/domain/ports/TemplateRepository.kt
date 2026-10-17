package com.tagforge.domain.ports

import com.tagforge.domain.template.Template
import kotlinx.coroutines.flow.Flow

interface TemplateRepository {
    /** Alphabetical, case-insensitive. */
    fun observeAll(): Flow<List<Template>>

    suspend fun get(id: Long): Template?

    /** Inserts when [Template.id] is 0, otherwise replaces. Returns the id. */
    suspend fun upsert(template: Template): Long

    suspend fun delete(id: Long)
}
