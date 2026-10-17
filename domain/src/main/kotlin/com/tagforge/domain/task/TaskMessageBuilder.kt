package com.tagforge.domain.task

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.ndef.BudgetReport
import com.tagforge.domain.ndef.NdefBudget
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.Tnf
import com.tagforge.domain.ntag.NtagModel

/** Why the Write button of the task builder is off. The UI turns each into an explanation. */
enum class TaskBlock { NO_ACTIONS, INVALID_ACTION, ENCODE_ERROR, OVER_CAPACITY }

/**
 * What the builder knows about a draft right now.
 * @property message the message to write (task record + AAR), or null when [block] is set for a reason other than size
 * @property budget size accounting for the meter; computed even for a blocked draft whenever it can be encoded
 * @property invalid index of each blocked action and why
 */
data class TaskAnalysis(
    val message: NdefMessage?,
    val budget: BudgetReport,
    val invalid: Map<Int, BlockReason>,
    val error: TaskError?,
    val block: TaskBlock?,
    val suggestedChip: NtagModel?,
)

/**
 * The NDEF layout of a task tag (TK-03): `[MIME application/x-tf = TaskCodec payload, AAR = this app]`.
 * The AAR is never first because Android dispatches on record 0, and it makes a phone without Tag Forge offer to
 * install it. The task type is 16 bytes (design ADR 7), so the overhead is 19 + 34 and an NTAG213 holds P <= 83
 * payload bytes on the 136-byte preset budget (84 against Android's 137, which allows an exact fit).
 */
object TaskMessageBuilder {

    const val MIME_TYPE = "application/x-tf"

    private const val SHORT_RECORD_HEADER = 3     // flags + type length + 1-byte payload length
    private const val LONG_RECORD_HEADER = 6      // flags + type length + 4-byte payload length
    private const val MAX_SHORT_PAYLOAD = 255
    private const val AAR_TYPE_LENGTH = 15        // "android.com:pkg"

    fun build(payload: TaskPayload, appPackage: String): Outcome<NdefMessage, TaskError> =
        when (val encoded = TaskCodec.encode(payload)) {
            is Outcome.Err -> encoded
            is Outcome.Ok -> Outcome.Ok(NdefMessage(listOf(NdefRecord.mime(MIME_TYPE, encoded.value), NdefRecord.aar(appPackage))))
        }

    /** The first record that carries a task, or null. The type is matched ignoring case, as MIME types are. */
    fun find(message: NdefMessage): NdefRecord? = message.records.firstOrNull(::isTaskRecord)

    fun isTaskRecord(record: NdefRecord): Boolean =
        record.tnf == Tnf.MIME && String(record.type.toByteArray(), Charsets.US_ASCII).equals(MIME_TYPE, ignoreCase = true)

    /**
     * The same task record with every Wi-Fi join key blanked, for history (passwords never stored, HT-01). A payload
     * that does not decode is dropped entirely: it must never be stored as-is, a key could be hiding in it.
     */
    fun redactKeys(record: NdefRecord): NdefRecord {
        val payload = (TaskCodec.decode(record.payload) as? Outcome.Ok)?.value
            ?: return record.copy(payload = Bytes.EMPTY)
        if (payload.actions.none { it is TaskAction.WifiJoin && it.key.isNotEmpty() }) return record
        val cleaned = payload.copy(actions = payload.actions.map { if (it is TaskAction.WifiJoin) it.copy(key = "") else it })
        val encoded = (TaskCodec.encode(cleaned) as? Outcome.Ok)?.value ?: Bytes.EMPTY
        return record.copy(payload = encoded)
    }

    /** Task from a scanned message: [TaskError.NotATask] when there is no task record, the codec's error when it is hostile. */
    fun extract(message: NdefMessage): Outcome<TaskPayload, TaskError> {
        val record = find(message) ?: return Outcome.Err(TaskError.NotATask)
        return TaskCodec.decode(record.payload)
    }

    /** Largest task payload that fits in [capacity] message bytes next to the task record header and the AAR. */
    fun maxPayload(capacity: Int, appPackage: String, mimeType: String = MIME_TYPE): Int {
        val aar = SHORT_RECORD_HEADER + AAR_TYPE_LENGTH + appPackage.length
        val short = capacity - aar - SHORT_RECORD_HEADER - mimeType.length
        return if (short <= MAX_SHORT_PAYLOAD) short else capacity - aar - LONG_RECORD_HEADER - mimeType.length
    }

    /** Validates, encodes and sizes [draft] against [capacity] (see [NdefBudget.androidMaxSize] or a detected tag). */
    fun analyse(draft: TaskDraft, appPackage: String, capacity: Int): TaskAnalysis {
        val invalid = draft.actions.mapIndexedNotNull { index, action ->
            (ActionValidator.check(action) as? ActionCheck.Blocked)?.let { index to it.reason }
        }.toMap()

        val built = build(draft.payload, appPackage)
        val message = (built as? Outcome.Ok)?.value
        val error = (built as? Outcome.Err)?.error
        val budget = NdefBudget.budget(message ?: NdefMessage.EMPTY, capacity)

        val block = when {
            draft.actions.isEmpty() -> TaskBlock.NO_ACTIONS
            invalid.isNotEmpty() -> TaskBlock.INVALID_ACTION
            error != null -> TaskBlock.ENCODE_ERROR
            !budget.fits -> TaskBlock.OVER_CAPACITY
            else -> null
        }
        return TaskAnalysis(
            message = message.takeIf { block == null },
            budget = budget,
            invalid = invalid,
            error = error,
            block = block,
            suggestedChip = if (block == TaskBlock.OVER_CAPACITY) NdefBudget.smallestChipFor(budget.used) else null,
        )
    }
}
