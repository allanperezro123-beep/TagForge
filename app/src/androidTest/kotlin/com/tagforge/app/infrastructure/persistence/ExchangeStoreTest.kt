package com.tagforge.app.infrastructure.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.exchange.DataExchange
import com.tagforge.domain.exchange.SectionCount
import com.tagforge.domain.history.HistoryEntry
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryOutcome
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.template.Template
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Instant

/** Instrumented: the real transaction. 50 history + 5 templates export, wipe, import => 55 restored (HT-04). */
@RunWith(AndroidJUnit4::class)
class ExchangeStoreTest {

    private lateinit var db: TagForgeDatabase
    private lateinit var exchange: DataExchange

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TagForgeDatabase::class.java).build()
        exchange = DataExchange(RoomExchangeStore(db), Clock.System)
    }

    @After
    fun close() = db.close()

    @Test
    fun fiftyHistoryAndFiveTemplatesSurviveAWipe() = runBlocking {
        val history = RoomHistoryRepository(db.historyDao())
        val templates = RoomTemplateRepository(db.templateDao())
        repeat(50) {
            history.add(
                HistoryEntry(
                    at = Instant.fromEpochMilliseconds(1_000L * (it + 1)),
                    kind = HistoryKind.WRITE,
                    outcome = HistoryOutcome.SUCCESS,
                    uid = Bytes.of(4, it, 2, 3),
                    tagType = "NTAG213",
                    summary = "entry $it",
                    ndef = null,
                ),
            )
        }
        repeat(5) {
            templates.upsert(Template(name = "T$it", records = listOf(RecordSpec.Text("x$it")), createdAt = Instant.fromEpochMilliseconds(1), updatedAt = Instant.fromEpochMilliseconds(1)))
        }
        val file = exchange.exportJson().text
        val before = RoomExchangeStore(db).snapshot()

        history.clear()
        before.templates.forEach { templates.delete(it.id) }
        val report = (exchange.import(file) as Outcome.Ok).value

        assertEquals(SectionCount(50, 0), report.history)
        assertEquals(SectionCount(5, 0), report.templates)
        val after = RoomExchangeStore(db).snapshot()
        assertEquals(before.history.toSet(), after.history.toSet())
        assertEquals(before.templates.toSet(), after.templates.toSet())

        // importing again changes nothing: every id is already there
        val again = (exchange.import(file) as Outcome.Ok).value
        assertEquals(55, again.skipped)
        assertTrue(RoomExchangeStore(db).snapshot().history.size == 50)
    }
}
