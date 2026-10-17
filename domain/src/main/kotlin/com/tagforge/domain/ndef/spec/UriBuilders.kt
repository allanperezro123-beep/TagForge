package com.tagforge.domain.ndef.spec

import java.math.BigDecimal

/** Builds the URI strings that the RTD-URI record then abbreviates with the NFC Forum prefix table. */
object UriBuilders {

    fun tel(number: String): String = "tel:${digitsOnly(number)}"

    fun mailto(to: String, subject: String = "", body: String = ""): String =
        "mailto:${to.trim()}" + query("subject" to subject, "body" to body)

    fun sms(number: String, body: String = ""): String = "sms:${digitsOnly(number)}" + query("body" to body)

    fun geo(latitude: Double, longitude: Double, query: String = ""): String =
        "geo:${plain(latitude)},${plain(longitude)}" + query("q" to query)

    /** Keeps a leading `+` and the digits; spaces, dashes, dots and brackets are formatting, not part of the number. */
    internal fun digitsOnly(number: String): String {
        val trimmed = number.trim()
        val digits = trimmed.filter { it in '0'..'9' }
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    /** `?a=b&c=d` for the non-empty pairs, or an empty string. Values are percent-encoded. */
    private fun query(vararg pairs: Pair<String, String>): String {
        val present = pairs.filter { it.second.isNotEmpty() }
        if (present.isEmpty()) return ""
        return present.joinToString(separator = "&", prefix = "?") { (key, value) -> "$key=${percentEncode(value)}" }
    }

    /** RFC 3986: unreserved characters stay, everything else becomes UTF-8 `%XX` (so a space is `%20`, never `+`). */
    internal fun percentEncode(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val v = byte.toInt() and 0xFF
            val ch = v.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '~') {
                sb.append(ch)
            } else {
                sb.append('%').append("0123456789ABCDEF"[v ushr 4]).append("0123456789ABCDEF"[v and 0x0F])
            }
        }
        return sb.toString()
    }

    private fun plain(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
