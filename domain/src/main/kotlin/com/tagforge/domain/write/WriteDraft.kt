package com.tagforge.domain.write

import com.tagforge.domain.ndef.BudgetReport
import com.tagforge.domain.ndef.NdefBudget
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.spec.FieldError
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.RecordSpecEncoder
import com.tagforge.domain.ndef.spec.RecordSpecValidator

/** Why the Write button is disabled. The UI turns each into an explanation. */
enum class WriteBlock { NO_RECORDS, INVALID_RECORD, OVER_CAPACITY }

/**
 * What the wizard knows about a draft message right now.
 * @property message the encodable message (AAR included), or null when there are no records or one is invalid
 * @property budget size accounting over the records that DO encode, so the meter works while another is incomplete
 * @property invalid field errors per record index
 */
data class DraftAnalysis(
    val message: NdefMessage?,
    val budget: BudgetReport,
    val invalid: Map<Int, List<FieldError>>,
    val block: WriteBlock?,
)

/** The message being composed: an ordered list of record specs plus the optional AAR. Immutable; edits return a copy. */
data class WriteDraft(
    val records: List<RecordSpec> = emptyList(),
    val includeAar: Boolean = false,
) {
    fun add(spec: RecordSpec): WriteDraft = copy(records = records + spec)

    fun replace(index: Int, spec: RecordSpec): WriteDraft =
        if (index !in records.indices) this else copy(records = records.toMutableList().also { it[index] = spec })

    fun remove(index: Int): WriteDraft =
        if (index !in records.indices) this else copy(records = records.toMutableList().also { it.removeAt(index) })

    /** Moves the record one step ([delta] = -1 up, +1 down); at either end nothing changes. */
    fun move(index: Int, delta: Int): WriteDraft {
        val target = index + delta
        if (index !in records.indices || target !in records.indices) return this
        return copy(records = records.toMutableList().also { it.add(target, it.removeAt(index)) })
    }

    /**
     * @param capacity message capacity of the chosen target (see [NdefBudget.androidMaxSize] or a detected tag)
     * @param pending the record currently being typed: counted when it is complete, ignored while it is not
     */
    fun analyse(capacity: Int, appPackage: String, pending: RecordSpec? = null): DraftAnalysis {
        val invalid = RecordSpecValidator.validateAll(records)
        val saved = records.map(RecordSpecEncoder::encode)
        val pendingRecord = pending?.let(RecordSpecEncoder::encode)
        val counted: List<NdefRecord> =
            saved.filterNotNull() + listOfNotNull(pendingRecord) + if (includeAar) listOf(NdefRecord.aar(appPackage)) else emptyList()

        val budget = NdefBudget.budget(NdefMessage(counted), capacity)
        val message = if (invalid.isEmpty() && records.isNotEmpty()) RecordSpecEncoder.encodeMessage(records, appPackage.takeIf { includeAar }) else null
        val block = when {
            records.isEmpty() -> WriteBlock.NO_RECORDS
            invalid.isNotEmpty() -> WriteBlock.INVALID_RECORD
            !budget.fits -> WriteBlock.OVER_CAPACITY
            else -> null
        }
        return DraftAnalysis(message, budget, invalid, block)
    }
}
