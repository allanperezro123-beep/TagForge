package com.tagforge.domain.ndef

/** NFC Forum URI Record Type Definition abbreviation table (codes 0x00..0x23). */
object UriPrefixes {

    private val TABLE: List<String> = listOf(
        "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
        "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://", "nfs://", "ftp://",
        "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:", "pop:", "sip:", "sips:", "tftp:",
        "btspp://", "btl2cap://", "btgoep://", "tcpobex://", "irdaobex://", "file://",
        "urn:epc:id:", "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:",
    )

    /** Codes ordered by prefix length, longest first, so the first match is the longest match. */
    private val LONGEST_FIRST: List<Int> =
        (1 until TABLE.size).sortedByDescending { TABLE[it].length }

    data class Split(val code: Int, val rest: String)

    /** Prefix text for [code], or null when the code is outside the NFC Forum table. */
    fun prefixFor(code: Int): String? = TABLE.getOrNull(code)

    /** Splits [uri] into the longest matching abbreviation code and the remainder (code 0 = no abbreviation). */
    fun split(uri: String): Split {
        for (code in LONGEST_FIRST) {
            val prefix = TABLE[code]
            if (uri.startsWith(prefix)) return Split(code, uri.substring(prefix.length))
        }
        return Split(0x00, uri)
    }

    /** Re-joins a [code] and [rest]; null when the code is unknown. */
    fun join(code: Int, rest: String): String? = prefixFor(code)?.let { it + rest }
}
