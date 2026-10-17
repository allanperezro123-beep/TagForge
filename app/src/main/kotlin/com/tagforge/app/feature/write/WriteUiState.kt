package com.tagforge.app.feature.write

import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.ParsedRecord
import com.tagforge.domain.ndef.spec.FieldError
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.RecordSpecValidator
import com.tagforge.domain.ndef.spec.SpecField
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.write.DraftAnalysis
import com.tagforge.domain.write.WriteReport

enum class WriteStep { TYPE, FORM, REVIEW }

/** The tag the size meter measures against. */
sealed interface WriteTarget {
    /** A chip the user picked before tapping anything. */
    data class Chip(val model: NtagModel) : WriteTarget

    /** The real capacity reported by the last tag that was tapped. */
    data class LastTag(val maxSize: Int) : WriteTarget
}

/**
 * The form being filled. [index] is the position of the record being edited, or null for a new one.
 * Errors are computed from [spec] on demand; [visibleErrors] hides the ones for fields the user has not reached yet.
 */
data class EditingRecord(
    val index: Int?,
    val spec: RecordSpec,
    val touched: Set<SpecField> = emptySet(),
    val showAllErrors: Boolean = false,
) {
    val errors: List<FieldError> get() = RecordSpecValidator.validate(spec)
    val visibleErrors: List<FieldError> get() = if (showAllErrors) errors else errors.filter { it.field in touched }
    val isValid: Boolean get() = errors.isEmpty()
}

/** What the scan sheet shows while a write is armed or running. */
sealed interface WriteSheet {
    data class Waiting(val messageSize: Int) : WriteSheet
    data class Working(val phase: OperationPhase) : WriteSheet
    data class Success(val report: WriteReport) : WriteSheet

    /** [canRetry] is false when trying again with the same tag cannot help (locked, too small). */
    data class Failure(val error: TagError, val canRetry: Boolean) : WriteSheet
}

/** The tag already holds data. [existingSummary] is null when that data could not be read. */
data class OverwritePrompt(val existingSummary: String?)

/** One-off signals the screen turns into haptics and snackbars. */
sealed interface WriteEvent {
    data object TagDetected : WriteEvent
    data object Succeeded : WriteEvent
    data object Failed : WriteEvent
    data class TemplateSaved(val name: String) : WriteEvent
}

/** Everything the Write screen can show. The derived fields are recomputed by the ViewModel on every change. */
data class WriteUiState(
    val availability: NfcAvailability = NfcAvailability.ENABLED,
    val step: WriteStep = WriteStep.TYPE,
    val records: List<RecordSpec> = emptyList(),
    val includeAar: Boolean = false,
    val target: WriteTarget = WriteTarget.Chip(NtagModel.NTAG213),
    val lastTagCapacity: Int? = null,
    val editing: EditingRecord? = null,
    val sheet: WriteSheet? = null,
    val overwrite: OverwritePrompt? = null,
    val canUndoRemove: Boolean = false,
    // ---- derived ----
    val capacity: Int = 0,
    val analysis: DraftAnalysis,
    val previewParsed: ParsedRecord? = null,
    val previewHex: String? = null,
    val recordPreviews: List<ParsedRecord?> = emptyList(),
    val messageHex: String? = null,
    val suggestedChip: NtagModel? = null,
    val canWrite: Boolean = false,
) {
    val message: NdefMessage? get() = analysis.message
}
