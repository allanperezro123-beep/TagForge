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

        /** TNF 0x02 record: the media type is the TYPE field. */
        fun mime(mimeType: String, payload: Bytes): NdefRecord =
            NdefRecord(Tnf.MIME, Bytes.copyOf(mimeType.toByteArray(Charsets.US_ASCII)), payload = payload)

        /** `text/vcard` record holding an already-built vCard document. */
        fun vcard(document: String): NdefRecord = mime(MIME_VCARD, Bytes.copyOf(document.toByteArray(Charsets.UTF_8)))

        /** TNF 0x04 record whose type is `domain:type`. */
        fun external(domain: String, type: String, payload: Bytes): NdefRecord =
            NdefRecord(Tnf.EXTERNAL, Bytes.copyOf("$domain:$type".toByteArray(Charsets.US_ASCII)), payload = payload)

        /** Android Application Record: launches (or offers to install) [packageName]. Should be the LAST record. */
        fun aar(packageName: String): NdefRecord =
            external(AAR_DOMAIN, AAR_TYPE, Bytes.copyOf(packageName.toByteArray(Charsets.US_ASCII)))

        const val MIME_VCARD = "text/vcard"
        const val AAR_DOMAIN = "android.com"
        const val AAR_TYPE = "pkg"

        val TYPE_TEXT: Bytes = Bytes.of(0x54)
        val TYPE_URI: Bytes = Bytes.of(0x55)
        private const val MAX_LANGUAGE_LENGTH = 63
    }
}
