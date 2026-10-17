package com.tagforge.app.infrastructure.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.history.HistoryEntry
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryOutcome
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.task.RingerMode
import com.tagforge.domain.task.Task
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.template.Template
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

/** Instrumented (outside the JVM gate): real SQLite, real Room-generated code. Run with `connectedDebugAndroidTest`. */
@RunWith(AndroidJUnit4::class)
class DaoTest {

    private lateinit var db: TagForgeDatabase
    private lateinit var history: RoomHistoryRepository
    private lateinit var templates: RoomTemplateRepository
    private lateinit var tasks: RoomTaskRepository

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TagForgeDatabase::class.java).build()
        history = RoomHistoryRepository(db.historyDao())
        templates = RoomTemplateRepository(db.templateDao())
        tasks = RoomTaskRepository(db.taskDao())
    }

    @After
    fun close() = db.close()

    private fun entry(at: Long, kind: HistoryKind = HistoryKind.READ, outcome: HistoryOutcome = HistoryOutcome.SUCCESS) =
        HistoryEntry(
            at = Instant.fromEpochMilliseconds(at),
            kind = kind,
            outcome = outcome,
            uid = Bytes.of(4, 1, 2, 3),
            tagType = "NTAG213",
            summary = "entry $at",
            ndef = Bytes.of(0xD0, 0, 0),
        )

    @Test
    fun historyIsListedNewestFirstAndRoundTrips() = runBlocking {
        history.add(entry(1_000))
        history.add(entry(3_000, HistoryKind.WRITE, HistoryOutcome.FAILED_TAGLOST))
        history.add(entry(2_000))

        val listed = history.observe().first()

        assertEquals(listOf(3_000L, 2_000L, 1_000L), listed.map { it.at.toEpochMilliseconds() })
        assertEquals(HistoryOutcome.FAILED_TAGLOST, listed.first().outcome)
        assertEquals(Bytes.of(0xD0, 0, 0), listed.first().ndef)
    }

    @Test
    fun clearingHistoryLeavesTemplatesUntouched() = runBlocking {
        history.add(entry(1_000))
        templates.upsert(Template(name = "WiFi casa", records = listOf(RecordSpec.Text("hi")), createdAt = Instant.fromEpochMilliseconds(1), updatedAt = Instant.fromEpochMilliseconds(1)))

        history.clear()

        assertEquals(0, history.observe().first().size)
        assertEquals(1, templates.observeAll().first().size)
    }

    @Test
    fun templateUpsertInsertsThenUpdatesInPlace() = runBlocking {
        val id = templates.upsert(Template(name = "B", records = listOf(RecordSpec.Text("one")), createdAt = Instant.fromEpochMilliseconds(1), updatedAt = Instant.fromEpochMilliseconds(1)))
        templates.upsert(templates.get(id)!!.copy(name = "A", records = listOf(RecordSpec.Text("two"))))

        val all = templates.observeAll().first()

        assertEquals(1, all.size)
        assertEquals("A", all.single().name)
        assertEquals(listOf<RecordSpec>(RecordSpec.Text("two")), all.single().records)
    }

    @Test
    fun templatesAreListedAlphabeticallyIgnoringCase() = runBlocking {
        for (name in listOf("banana", "Apple", "cherry")) {
            templates.upsert(Template(name = name, records = emptyList(), createdAt = Instant.fromEpochMilliseconds(1), updatedAt = Instant.fromEpochMilliseconds(1)))
        }
        assertEquals(listOf("Apple", "banana", "cherry"), templates.observeAll().first().map { it.name })
    }

    @Test
    fun deletedTemplateIsGone() = runBlocking {
        val id = templates.upsert(Template(name = "x", records = emptyList(), createdAt = Instant.fromEpochMilliseconds(1), updatedAt = Instant.fromEpochMilliseconds(1)))
        templates.delete(id)
        assertNull(templates.get(id))
    }

    @Test
    fun aSavedTaskIsListedWithIdenticalActionsAfterTheDatabaseIsReopened() = runBlocking {
        val actions = listOf(TaskAction.SetRinger(RingerMode.VIBRATE), TaskAction.OpenUrl("https://example.com"))
        val id = tasks.upsert(Task(name = "Night", actions = actions, createdAt = Instant.fromEpochMilliseconds(1), updatedAt = Instant.fromEpochMilliseconds(1)))

        val loaded = tasks.get(id)!!
        assertEquals(actions, loaded.actions)
        assertEquals(listOf("Night"), tasks.observeAll().first().map { it.name })

        tasks.markRun(id, Instant.fromEpochMilliseconds(99))
        assertEquals(99L, tasks.get(id)!!.lastRunAt!!.toEpochMilliseconds())

        tasks.delete(id)
        assertNull(tasks.get(id))
    }
}
