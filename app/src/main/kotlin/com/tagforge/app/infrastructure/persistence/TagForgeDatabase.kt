package com.tagforge.app.infrastructure.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tagforge.app.infrastructure.persistence.dao.HistoryDao
import com.tagforge.app.infrastructure.persistence.dao.TaskDao
import com.tagforge.app.infrastructure.persistence.dao.TemplateDao
import com.tagforge.app.infrastructure.persistence.entity.HistoryEntity
import com.tagforge.app.infrastructure.persistence.entity.TaskEntity
import com.tagforge.app.infrastructure.persistence.entity.TemplateEntity

/**
 * Schema v1 holds all three tables so no migration is needed mid-project. The schema JSON is exported to
 * `app/schemas` and committed. POLICY: never `fallbackToDestructiveMigration`; every version bump ships an explicit
 * `Migration` (or `@AutoMigration`) plus a MigrationTestHelper test.
 */
@Database(
    entities = [HistoryEntity::class, TemplateEntity::class, TaskEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TagForgeDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun templateDao(): TemplateDao
    abstract fun taskDao(): TaskDao

    companion object {
        const val NAME = "tagforge.db"

        fun create(context: Context): TagForgeDatabase =
            Room.databaseBuilder(context.applicationContext, TagForgeDatabase::class.java, NAME).build()
    }
}
