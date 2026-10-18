package com.tagforge.domain.exchange

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Hex
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.history.HistoryEntry
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryOutcome
import com.tagforge.domain.history.HistorySummarizer
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.RecordSpecJson
import com.tagforge.domain.task.ActionCheck
import com.tagforge.domain.task.ActionValidator
import com.tagforge.domain.task.Task
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.task.TaskLimits
import com.tagforge.domain.template.Template
import com.tagforge.domain.template.TemplateNames
import kotlin.time.Instant

/**
 * Domain object <-> wire object. Export removes every secret; import trusts nothing: each value is checked, tasks are
 * re-validated like a task read from a tag, and stored NDEF bytes are stripped of network keys again.
 */
internal object ExchangeConversions {

    const val MAX_SUMMARY = 500
    const val MAX_TAG_TYPE = 40
    const val MAX_NDEF_BYTES = 4_096
    const val MAX_UID_BYTES = 10
    const val MAX_TASK_NAME = 200
    const val MAX_TEMPLATE_RECORDS = 32
    const val MAX_TEMPLATE_JSON = 64 * 1024

    // ---- export ----------------------------------------------------------------------------------------------------

    fun toDto(entry: HistoryEntry) = HistoryDto(
        id = entry.id,
        at = entry.at.toEpochMilliseconds(),
        kind = entry.kind.code,
        outcome = entry.outcome.code,
        uid = entry.uid?.toHex(),
        tagType = entry.tagType,
        summary = entry.summary,
        ndef = entry.ndef?.let(::redactedNdef)?.toHex(),
    )

    fun toDto(template: Template) = TemplateDto(
        id = template.id,
        name = template.name,
        records = template.records.map { if (it is RecordSpec.Wifi) it.copy(password = "") else it },
        createdAt = template.createdAt.toEpochMilliseconds(),
        updatedAt = template.updatedAt.toEpochMilliseconds(),
    )

    fun toDto(task: Task) = TaskDto(
        id = task.id,
        name = task.name,
        actions = task.actions.map { if (it is TaskAction.WifiJoin) it.copy(key = "") else it },
        createdAt = task.createdAt.toEpochMilliseconds(),
        updatedAt = task.updatedAt.toEpochMilliseconds(),
        lastRunAt = task.lastRunAt?.toEpochMilliseconds(),
    )

    /** Bytes that no longer parse are dropped rather than exported unchecked: they could hide a key. */
    private fun redactedNdef(bytes: Bytes): Bytes? = when (val decoded = DefaultNdefCodec.decode(bytes)) {
        is Outcome.Err -> null
        is Outcome.Ok -> {
            val safe = HistorySummarizer.redact(decoded.value)
            if (safe == decoded.value) bytes else DefaultNdefCodec.encode(safe)
        }
    }

    // ---- import ----------------------------------------------------------------------------------------------------

    fun history(dto: HistoryDto): Outcome<HistoryEntry, EntryProblem> {
        if (dto.id <= 0) return bad(EntryProblem.INVALID_VALUE)
        if (dto.summary.length > MAX_SUMMARY || (dto.tagType?.length ?: 0) > MAX_TAG_TYPE) return bad(EntryProblem.TOO_LONG)
        val kind = HistoryKind.entries.firstOrNull { it.code == dto.kind } ?: return bad(EntryProblem.UNKNOWN_KIND)
        val outcome = HistoryOutcome.entries.firstOrNull { it.code == dto.outcome } ?: return bad(EntryProblem.UNKNOWN_OUTCOME)
        val at = instant(dto.at) ?: return bad(EntryProblem.INVALID_VALUE)

        val uid = dto.uid?.let { hex ->
            Hex.parse(hex)?.takeIf { it.size in 1..MAX_UID_BYTES } ?: return bad(EntryProblem.BAD_HEX)
        }
        val ndef = dto.ndef?.let { hex ->
            val raw = Hex.parse(hex) ?: return bad(EntryProblem.BAD_HEX)
            if (raw.size > MAX_NDEF_BYTES) return bad(EntryProblem.TOO_LONG)
            safeNdef(raw) ?: return bad(EntryProblem.UNREADABLE_NDEF)
        }
        return Outcome.Ok(HistoryEntry(dto.id, at, kind, outcome, uid, dto.tagType, dto.summary, ndef))
    }

    /** The bytes to keep, or null when they are not a readable NDEF message. */
    private fun safeNdef(raw: Bytes): Bytes? {
        val message = (DefaultNdefCodec.decode(raw) as? Outcome.Ok)?.value ?: return null
        val safe = HistorySummarizer.redact(message)
        return if (safe == message) raw else DefaultNdefCodec.encode(safe)
    }

    fun template(dto: TemplateDto): Outcome<Template, EntryProblem> {
        if (dto.id <= 0) return bad(EntryProblem.INVALID_VALUE)
        if (dto.name.trim().length > TemplateNames.MAX_LENGTH) return bad(EntryProblem.TOO_LONG)
        val name = TemplateNames.resolve(dto.name, emptyList()) ?: return bad(EntryProblem.INVALID_VALUE)
        if (dto.records.size > MAX_TEMPLATE_RECORDS) return bad(EntryProblem.INVALID_VALUE)
        if (RecordSpecJson.encode(dto.records).length > MAX_TEMPLATE_JSON) return bad(EntryProblem.TOO_LONG)
        val created = instant(dto.createdAt) ?: return bad(EntryProblem.INVALID_VALUE)
        val updated = instant(dto.updatedAt) ?: return bad(EntryProblem.INVALID_VALUE)
        // A Wi-Fi key can never arrive through a file, whatever the file says.
        val records = dto.records.map { if (it is RecordSpec.Wifi) it.copy(password = "") else it }
        return Outcome.Ok(Template(dto.id, name, records, created, updated))
    }

    fun task(dto: TaskDto): Outcome<Task, EntryProblem> {
        if (dto.id <= 0) return bad(EntryProblem.INVALID_VALUE)
        if (dto.name.length > MAX_TASK_NAME) return bad(EntryProblem.TOO_LONG)
        if (dto.actions.size > TaskLimits.MAX_ACTIONS) return bad(EntryProblem.INVALID_VALUE)
        if (dto.actions.any { isBlocked(it) }) return bad(EntryProblem.BLOCKED_ACTION)
        val created = instant(dto.createdAt) ?: return bad(EntryProblem.INVALID_VALUE)
        val updated = instant(dto.updatedAt) ?: return bad(EntryProblem.INVALID_VALUE)
        val lastRun = dto.lastRunAt?.let { instant(it) ?: return bad(EntryProblem.INVALID_VALUE) }
        val actions = dto.actions.map { if (it is TaskAction.WifiJoin) it.copy(key = "") else it }
        return Outcome.Ok(Task(dto.id, dto.name, actions, created, updated, lastRun))
    }

    /**
     * Same gate as a task read from a tag. The one difference: a Wi-Fi key is never in a file (export removes it), so
     * a missing key is not by itself a reason to refuse; every other part of the action is still checked.
     */
    private fun isBlocked(action: TaskAction): Boolean {
        val checked = if (action is TaskAction.WifiJoin && action.key.isEmpty()) action.copy(key = PLACEHOLDER_KEY) else action
        return ActionValidator.check(checked) is ActionCheck.Blocked
    }

    private const val PLACEHOLDER_KEY = "placeholder-key"

    private fun instant(millis: Long): Instant? =
        if (millis < 0) null else runCatching { Instant.fromEpochMilliseconds(millis) }.getOrNull()

    private fun bad(problem: EntryProblem): Outcome.Err<EntryProblem> = Outcome.Err(problem)
}
