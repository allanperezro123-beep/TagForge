package com.tagforge.app.infrastructure.persistence

import com.tagforge.app.infrastructure.persistence.entity.TaskEntity
import com.tagforge.domain.task.RingerMode
import com.tagforge.domain.task.Task
import com.tagforge.domain.task.TaskAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** TK-10 "Persist": a task comes back from its row with identical actions; the row keeps JSON, not a blob (reconciliation #7). */
class TaskMapperTest {

    private val created = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val updated = Instant.fromEpochMilliseconds(1_700_000_005_000)

    private val task = Task(
        id = 4,
        name = "Night mode",
        actions = listOf(
            TaskAction.SetRinger(RingerMode.VIBRATE),
            TaskAction.OpenUrl("https://example.com/ñ"),
            TaskAction.Alarm(7, 30, "Wake up"),
        ),
        createdAt = created,
        updatedAt = updated,
        lastRunAt = Instant.fromEpochMilliseconds(1_700_000_009_000),
    )

    @Test
    fun theRowStoresActionsAsJsonAndTimesAsEpochMillis() {
        val entity = task.toEntity()

        assertEquals(4, entity.id)
        assertEquals("Night mode", entity.name)
        assertTrue("\"t\":\"ringer\"" in entity.actionsJson, entity.actionsJson)
        assertTrue("\"t\":\"open_url\"" in entity.actionsJson, entity.actionsJson)
        assertEquals(1_700_000_000_000, entity.createdAt)
        assertEquals(1_700_000_005_000, entity.updatedAt)
        assertEquals(1_700_000_009_000, entity.lastRunAt)
    }

    @Test
    fun aTaskRoundTripsThroughItsRowWithIdenticalActions() {
        assertEquals(task, task.toEntity().toDomain())
    }

    @Test
    fun aTaskThatNeverRanHasNoLastRun() {
        val never = task.copy(lastRunAt = null)

        assertNull(never.toEntity().lastRunAt)
        assertEquals(never, never.toEntity().toDomain())
    }

    @Test
    fun unreadableJsonLoadsAsAnEmptyTaskSoItsNameStaysVisibleForDeleting() {
        val broken = TaskEntity(id = 9, name = "Old", actionsJson = """[{"t":"teleport"}]""", createdAt = 1, updatedAt = 2, lastRunAt = null)

        val loaded = broken.toDomain()

        assertEquals("Old", loaded.name)
        assertEquals(emptyList(), loaded.actions)
        assertEquals(9, loaded.id)
    }
}
