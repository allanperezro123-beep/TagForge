package com.tagforge.domain.ndef

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ndef.spec.BluetoothAddress
import com.tagforge.domain.ndef.spec.VCardFields
import com.tagforge.domain.ndef.spec.WifiCredentials

/**
 * Human-meaningful interpretation of an [NdefRecord]. Every variant keeps its source [record] so the UI
 * can always offer the raw-hex expander.
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

    data class Wifi(override val record: NdefRecord, val credentials: WifiCredentials) : ParsedRecord

    data class Bluetooth(override val record: NdefRecord, val address: BluetoothAddress) : ParsedRecord

    data class VCard(override val record: NdefRecord, val fields: VCardFields) : ParsedRecord

    /** Android Application Record: the OS launches [packageName] when the tag is tapped. */
    data class Aar(override val record: NdefRecord, val packageName: String) : ParsedRecord

    data class SmartPoster(override val record: NdefRecord, val uri: String, val title: String?) : ParsedRecord

    data class Unknown(override val record: NdefRecord) : ParsedRecord
}
