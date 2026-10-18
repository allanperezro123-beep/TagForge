package com.tagforge.domain.exchange

import com.tagforge.domain.history.HistoryEntry
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.task.Task
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.template.Template
import kotlinx.serialization.Serializable

/** Everything the user can back up (HT-04). */
data class ExchangeData(
    val history: List<HistoryEntry>,
    val templates: List<Template>,
    val tasks: List<Task>,
) {
    val isEmpty: Boolean get() = history.isEmpty() && templates.isEmpty() && tasks.isEmpty()
    val size: Int get() = history.size + templates.size + tasks.size

    companion object {
        val EMPTY = ExchangeData(emptyList(), emptyList(), emptyList())
    }
}

enum class ExchangeSection { HISTORY, TEMPLATES, TASKS }

/** Why one entry of an import file was refused. The UI turns each into a sentence. */
enum class EntryProblem {
    /** Not the shape the schema describes: a missing field, a wrong type, an unknown action or record type. */
    BAD_STRUCTURE,
    UNKNOWN_KIND,
    UNKNOWN_OUTCOME,
    BAD_HEX,
    UNREADABLE_NDEF,

    /** A task action the runner would refuse: a disallowed URL scheme, a bad number or package name... */
    BLOCKED_ACTION,
    INVALID_VALUE,
    TOO_LONG,
}

/** Why a whole import file was refused. Nothing is imported in any of these cases. */
sealed interface ImportError {
    data object NotJson : ImportError
    data object NotABundle : ImportError
    data class UnsupportedSchema(val found: Int) : ImportError

    /** [number] is 1-based within [section], the way a person would count the entries. */
    data class BadEntry(val section: ExchangeSection, val number: Int, val problem: EntryProblem) : ImportError
    data class TooMany(val section: ExchangeSection) : ImportError
}

// ---- wire format (schema 1). Strings and numbers only, so the file reads the same on any future version. --------------

@Serializable
internal data class HistoryDto(
    val id: Long,
    val at: Long,
    val kind: String,
    val outcome: String,
    val uid: String? = null,
    val tagType: String? = null,
    val summary: String = "",
    val ndef: String? = null,
)

@Serializable
internal data class TemplateDto(
    val id: Long,
    val name: String,
    val records: List<RecordSpec> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
internal data class TaskDto(
    val id: Long,
    val name: String,
    val actions: List<TaskAction> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val lastRunAt: Long? = null,
)
