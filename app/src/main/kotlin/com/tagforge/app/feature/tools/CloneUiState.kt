package com.tagforge.app.feature.tools

import com.tagforge.app.feature.write.OverwritePrompt
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tools.CloneReport

enum class CloneStep { SOURCE, TARGET }

/** What the first tap captured, as the destination step shows it. */
data class CloneSourceSummary(
    val chip: String?,
    val uid: Bytes,
    val messageSize: Int,
    /** One-line description of the message (values only, WiFi keys never included). */
    val summary: String,
    val withUserPages: Boolean,
)

sealed interface CloneSheet {
    data class Waiting(val step: CloneStep) : CloneSheet
    data class Working(val step: CloneStep, val phase: OperationPhase) : CloneSheet
    data class Success(val report: CloneReport) : CloneSheet
    data class Failure(val step: CloneStep, val error: TagError, val canRetry: Boolean) : CloneSheet
}

sealed interface CloneEvent {
    data object TagDetected : CloneEvent
    data object SourceCaptured : CloneEvent
    data object Succeeded : CloneEvent
    data object Failed : CloneEvent
}

data class CloneUiState(
    val availability: NfcAvailability = NfcAvailability.ENABLED,
    val copyUserPages: Boolean = false,
    val step: CloneStep = CloneStep.SOURCE,
    val source: CloneSourceSummary? = null,
    val sheet: CloneSheet? = null,
    val overwrite: OverwritePrompt? = null,
) {
    val canStart: Boolean get() = availability == NfcAvailability.ENABLED
}
