package com.tagforge.app.feature.productivity

import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.exchange.BatchAnalysis
import com.tagforge.domain.exchange.BatchRun
import com.tagforge.domain.exchange.BatchSettings
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.template.Template

enum class BatchStep { SETUP, RUNNING, DONE }

/** What the running screen says about the current tag. */
sealed interface BatchStatus {
    /** Waiting for [label], the number the next tag will get. */
    data object Waiting : BatchStatus
    data class Working(val phase: OperationPhase) : BatchStatus

    /** The last tag was written and verified with [label]. */
    data class Written(val label: String) : BatchStatus

    /** The last attempt at [label] failed; the same number is tried again on the next tag. */
    data class Failed(val label: String, val error: TagError) : BatchStatus
}

/** A question the batch must have answered before it rewrites a tag (G5). */
sealed interface BatchPrompt {
    /** This tag was already written in this batch. */
    data object SameTag : BatchPrompt

    /** The tag holds other data. [existingSummary] is null when it could not be read. */
    data class Overwrite(val existingSummary: String?) : BatchPrompt
}

/** One-off signals for haptics. */
sealed interface BatchEvent {
    data object TagDetected : BatchEvent
    data object Succeeded : BatchEvent
    data object Failed : BatchEvent
}

/** Inputs stay text so a half-typed number is representable; the plan is built from the parsed values. */
data class BatchUiState(
    val step: BatchStep = BatchStep.SETUP,
    val availability: NfcAvailability = NfcAvailability.ENABLED,
    val templates: List<Template> = emptyList(),
    val selectedTemplateId: Long? = null,
    val records: List<RecordSpec> = emptyList(),
    val startText: String = "1",
    val stepText: String = "1",
    val padText: String = "1",
    val totalText: String = "10",
    val includeAar: Boolean = false,
    val target: NtagModel = NtagModel.NTAG213,
    // ---- derived ----
    val capacity: Int = 0,
    val usesCounter: Boolean = false,
    val analysis: BatchAnalysis = BatchAnalysis(null, null),
    val canStart: Boolean = false,
    // ---- running ----
    val run: BatchRun? = null,
    val status: BatchStatus = BatchStatus.Waiting,
    val prompt: BatchPrompt? = null,
) {
    /** The values the plan is built from; unparsable text becomes a value the plan reports as invalid. */
    val settings: BatchSettings
        get() = BatchSettings(
            start = startText.trim().toLongOrNull() ?: -1,
            step = stepText.trim().toLongOrNull() ?: 0,
            pad = padText.trim().toIntOrNull() ?: 0,
            total = totalText.trim().toIntOrNull() ?: 0,
        )
}
