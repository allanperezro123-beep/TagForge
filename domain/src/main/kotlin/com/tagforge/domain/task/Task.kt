package com.tagforge.domain.task

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/** Hard limits shared by the builder, the codec and the runner. */
object TaskLimits {
    const val MAX_ACTIONS = 16
    const val MAX_NAME_BYTES = 32
    const val MAX_STRING_BYTES = 255

    /** Anything bigger than this cannot be a task: the largest supported tag holds 868 bytes of NDEF. */
    const val MAX_PAYLOAD_BYTES = 1024
}

/** A saved task. [name] is what the user calls it; an empty name is not written to tags. */
data class Task(
    val id: Long = 0,
    val name: String,
    val actions: List<TaskAction>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastRunAt: Instant? = null,
) {
    /** What goes on a tag. */
    val payload: TaskPayload get() = TaskPayload(name.trim().ifEmpty { null }, actions)
}

/** The content of a task tag: an optional display name and the ordered actions. */
data class TaskPayload(val name: String?, val actions: List<TaskAction>)

/** The task being composed in the builder. Immutable; every edit returns a copy. */
data class TaskDraft(
    val name: String = "",
    val actions: List<TaskAction> = emptyList(),
) {
    val isFull: Boolean get() = actions.size >= TaskLimits.MAX_ACTIONS

    /** A full task does not grow: the builder disables "add" instead. */
    fun add(action: TaskAction): TaskDraft = if (isFull) this else copy(actions = actions + action)

    fun replace(index: Int, action: TaskAction): TaskDraft =
        if (index !in actions.indices) this else copy(actions = actions.toMutableList().also { it[index] = action })

    fun remove(index: Int): TaskDraft =
        if (index !in actions.indices) this else copy(actions = actions.toMutableList().also { it.removeAt(index) })

    /** Moves one step ([delta] = -1 up, +1 down); at either end nothing changes. */
    fun move(index: Int, delta: Int): TaskDraft {
        val target = index + delta
        if (index !in actions.indices || target !in actions.indices) return this
        return copy(actions = actions.toMutableList().also { it.add(target, it.removeAt(index)) })
    }

    val payload: TaskPayload get() = TaskPayload(name.trim().ifEmpty { null }, actions)
}

/** Tasks persist their actions as JSON (reconciliation #7). Unknown keys are ignored; anything unreadable is null. */
object TaskJson {

    private val json = Json {
        classDiscriminator = "t"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val serializer = ListSerializer(TaskAction.serializer())

    fun encode(actions: List<TaskAction>): String = json.encodeToString(serializer, actions)

    fun decode(text: String): List<TaskAction>? = runCatching { json.decodeFromString(serializer, text) }.getOrNull()
}
