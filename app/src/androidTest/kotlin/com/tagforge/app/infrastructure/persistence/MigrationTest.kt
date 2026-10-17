package com.tagforge.app.infrastructure.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration scaffold (HT-01). Today only v1 exists, so this proves the exported schema opens and keeps its rows.
 * When version 2 arrives: add the `Migration`, register it in `TagForgeDatabase`, and extend this test to
 * `createDatabase(NAME, 1)`, insert rows, `runMigrationsAndValidate(NAME, 2, true, MIGRATION_1_2)` and assert they survive.
 * Never `fallbackToDestructiveMigration`.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TagForgeDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun version1SchemaOpensAndKeepsInsertedRows() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO history (at, kind, summary, success) VALUES (1000, 'read', 'hello', 1)")
            close()
        }

        val reopened = helper.runMigrationsAndValidate(TEST_DB, 1, true)
        reopened.query("SELECT summary FROM history").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("hello", cursor.getString(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
