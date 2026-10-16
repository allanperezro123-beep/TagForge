package com.tagforge.domain.ndef

import com.tagforge.domain.common.Bytes

/**
 * Human-meaningful interpretation of an [NdefRecord]. Every variant keeps its source [record] so the UI
 * can always offer the raw-hex expander. Phase 2 adds Wifi, Bluetooth, VCard, Aar and SmartPoster.
 */
sealed interface ParsedRecord {
    val record: NdefRecord

    data class Text(override val record: NdefRecord, val languageCode: String, val text: String) : ParsedRecord

    /** [prefixKnown] is false when the abbreviation code is outside the NFC Forum table; [uri] is then the raw remainder. */
    data class Uri(override val record: NdefRecord, val uri: String, val prefixKnown: Boolean = true) : ParsedRecord

    data class Mime(override val record: NdefRecord, val mimeType: String, val payload: Bytes) : ParsedRecord

    data class External(
        override val record: NdefRecord,
        val domain: String,
        val type: String,
        val payload: Bytes,
    ) : ParsedRecord

    data class Unknown(override val record: NdefRecord) : ParsedRecord
}
