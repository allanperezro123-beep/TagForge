package com.tagforge.domain.location

import com.tagforge.domain.ndef.spec.RecordSpec

sealed interface SharedTextResult {
    /** The record to open the Write review with. Nothing is written until the user presses Write there. */
    data class Record(val spec: RecordSpec) : SharedTextResult

    data object Empty : SharedTextResult

    data object TooLong : SharedTextResult
}

/**
 * Turns the text another app shares ("Share > Tag Forge") into a record to review. The sender is untrusted, so this is
 * deliberately small: a length cap, a map or web link kept as a link, coordinates as a Geo record, everything else as text.
 * It never produces anything that runs: the result is only ever shown on the review step.
 */
object SharedText {
    const val MAX_CHARS = 2048

    fun parse(text: String?): SharedTextResult {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return SharedTextResult.Empty
        if (trimmed.length > MAX_CHARS) return SharedTextResult.TooLong
        val spec = when (val link = MapLinkParser.parse(trimmed)) {
            is MapLink.Coordinates -> RecordSpec.Geo(link.latitude, link.longitude, link.label)
            is MapLink.Web -> RecordSpec.Uri(link.url)
            MapLink.Unrecognized -> RecordSpec.Text(trimmed)
        }
        return SharedTextResult.Record(spec)
    }
}
