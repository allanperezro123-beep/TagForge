package com.tagforge.domain.exchange

import com.tagforge.domain.ndef.BudgetReport
import com.tagforge.domain.ndef.NdefBudget
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.RecordSpecEncoder
import com.tagforge.domain.ndef.spec.RecordSpecValidator

/** Counter settings of a batch: [total] tags numbered [start], [start]+[step], ... zero-padded to [pad] digits. */
data class BatchSettings(val start: Long = 1, val step: Long = 1, val pad: Int = 1, val total: Int = 10) {
    companion object {
        const val MAX_TOTAL = 10_000
        const val MAX_PAD = 12
        const val MAX_STEP = 1_000_000L
        const val MAX_COUNTER = 999_999_999_999L
    }
}

/** Why a batch cannot start. The UI turns each into an explanation. */
enum class BatchBlock { NO_RECORDS, INVALID_RECORD, BAD_COUNT, BAD_STEP, BAD_PAD, BAD_START, COUNTER_OVERFLOW, OVER_CAPACITY }

/**
 * @property budget size of the LONGEST rendering over the whole range, so a batch that would overflow on tag 998 is
 *   refused before tag 1 is written. Null when the template cannot be rendered at all.
 */
data class BatchAnalysis(val budget: BudgetReport?, val block: BatchBlock?)

/**
 * The same message written to many tags, with `{n}` replaced by a counter (PR-01). Only text, URL and MIME payloads
 * carry the token: every other field is copied as typed.
 */
class BatchPlan(
    val records: List<RecordSpec>,
    val includeAar: Boolean,
    val settings: BatchSettings,
) {

    val usesCounter: Boolean get() = records.any { spec ->
        when (spec) {
            is RecordSpec.Text -> TOKEN in spec.text
            is RecordSpec.Uri -> TOKEN in spec.uri
            is RecordSpec.Mime -> TOKEN in spec.payloadText
            else -> false
        }
    }

    fun counterAt(index: Int): Long = settings.start + index.toLong() * settings.step

    /** The counter as written on the tag: zero-padded, never truncated. */
    fun label(index: Int): String = counterAt(index).toString().padStart(settings.pad, '0')

    /** The records for tag number [index] (0-based within the batch). */
    fun render(index: Int): List<RecordSpec> {
        val label = label(index)
        return records.map { spec ->
            when (spec) {
                is RecordSpec.Text -> spec.copy(text = spec.text.replace(TOKEN, label))
                is RecordSpec.Uri -> spec.copy(uri = spec.uri.replace(TOKEN, label))
                is RecordSpec.Mime -> spec.copy(payloadText = spec.payloadText.replace(TOKEN, label))
                else -> spec
            }
        }
    }

    fun messageAt(index: Int, appPackage: String): NdefMessage? =
        RecordSpecEncoder.encodeMessage(render(index), appPackage.takeIf { includeAar })

    /**
     * Checks the settings and the template, then measures the longest message of the range against [capacity].
     * The counter only grows, so the longest rendering is the one for the last tag.
     */
    fun analyse(capacity: Int, appPackage: String): BatchAnalysis {
        val settingsProblem = settingsBlock()
        if (settingsProblem != null) return BatchAnalysis(null, settingsProblem)
        if (records.isEmpty()) return BatchAnalysis(null, BatchBlock.NO_RECORDS)

        val lastIndex = settings.total - 1
        val invalid = listOf(0, lastIndex).any { RecordSpecValidator.validateAll(render(it)).isNotEmpty() }
        if (invalid) return BatchAnalysis(null, BatchBlock.INVALID_RECORD)

        val longest = messageAt(lastIndex, appPackage) ?: return BatchAnalysis(null, BatchBlock.INVALID_RECORD)
        val budget = NdefBudget.budget(longest, capacity)
        return BatchAnalysis(budget, if (budget.fits) null else BatchBlock.OVER_CAPACITY)
    }

    private fun settingsBlock(): BatchBlock? = when {
        settings.total !in 1..BatchSettings.MAX_TOTAL -> BatchBlock.BAD_COUNT
        settings.step !in 1..BatchSettings.MAX_STEP -> BatchBlock.BAD_STEP
        settings.pad !in 1..BatchSettings.MAX_PAD -> BatchBlock.BAD_PAD
        settings.start < 0 -> BatchBlock.BAD_START
        counterAt(settings.total - 1) > BatchSettings.MAX_COUNTER -> BatchBlock.COUNTER_OVERFLOW
        else -> null
    }

    companion object {
        const val TOKEN = "{n}"
    }
}
