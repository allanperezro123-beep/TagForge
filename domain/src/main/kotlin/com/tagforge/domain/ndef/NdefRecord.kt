package com.tagforge.domain.ndef

import com.tagforge.domain.common.Bytes

data class NdefRecord(
    val tnf: Tnf,
    val type: Bytes,
    val id: Bytes = Bytes.EMPTY,
    val payload: Bytes,
) {
    companion object {
        /** NFC Forum Text RTD, UTF-8: status byte = language length, then ASCII language tag, then text. */
        fun text(text: String, languageCode: String = "en"): NdefRecord {
            val lang = languageCode.toByteArray(Charsets.US_ASCII)
            require(lang.size in 1..MAX_LANGUAGE_LENGTH) { "Language code must be 1..$MAX_LANGUAGE_LENGTH ASCII bytes" }
            val payload = Bytes.of(lang.size) + Bytes.copyOf(lang) + Bytes.copyOf(text.toByteArray(Charsets.UTF_8))
            return NdefRecord(Tnf.WELL_KNOWN, TYPE_TEXT, payload = payload)
        }

        /** NFC Forum URI RTD using the longest matching abbreviation code. */
        fun uri(uri: String): NdefRecord {
            val split = UriPrefixes.split(uri)
            val payload = Bytes.of(split.code) + Bytes.copyOf(split.rest.toByteArray(Charsets.UTF_8))
            return NdefRecord(Tnf.WELL_KNOWN, TYPE_URI, payload = payload)
        }

        val TYPE_TEXT: Bytes = Bytes.of(0x54)
        val TYPE_URI: Bytes = Bytes.of(0x55)
        private const val MAX_LANGUAGE_LENGTH = 63
    }
}
