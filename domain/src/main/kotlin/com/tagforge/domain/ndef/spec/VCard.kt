package com.tagforge.domain.ndef.spec

/** The vCard properties Tag Forge reads and writes. Only [name] is required. */
data class VCardFields(
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val org: String? = null,
    val url: String? = null,
    val note: String? = null,
)

/** vCard 3.0 (RFC 2426) builder and tolerant parser. Lines end in CRLF; text values are backslash-escaped. */
object VCard {

    private const val CRLF = "\r\n"
    private const val BEGIN = "BEGIN:VCARD"
    private const val END = "END:VCARD"
    private const val VERSION = "VERSION:3.0"

    fun build(fields: VCardFields): String {
        val lines = buildList {
            add(BEGIN)
            add(VERSION)
            add("FN:${escape(fields.name)}")
            fields.phone.nonBlank()?.let { add("TEL:${escape(it)}") }
            fields.email.nonBlank()?.let { add("EMAIL:${escape(it)}") }
            fields.org.nonBlank()?.let { add("ORG:${escape(it)}") }
            fields.url.nonBlank()?.let { add("URL:${escape(it)}") }
            fields.note.nonBlank()?.let { add("NOTE:${escape(it)}") }
            add(END)
        }
        return lines.joinToString(CRLF)
    }

    /** Null when [text] is not a vCard or has no formatted name (FN). Unknown properties and parameters are ignored. */
    fun parse(text: String): VCardFields? {
        val lines = unfold(text)
        if (lines.none { it.equals(BEGIN, ignoreCase = true) }) return null

        val values = HashMap<String, String>()
        for (line in lines) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val property = line.substring(0, colon).substringBefore(';').uppercase()
            values.getOrPut(property) { unescape(line.substring(colon + 1)) }
        }
        val name = values["FN"]?.takeIf { it.isNotBlank() } ?: return null
        return VCardFields(name, values["TEL"], values["EMAIL"], values["ORG"], values["URL"], values["NOTE"])
    }

    /** Joins RFC 2425 folded lines (continuations start with a space or tab) and splits on CRLF or LF. */
    private fun unfold(text: String): List<String> {
        val out = ArrayList<String>()
        for (raw in text.split("\r\n", "\n")) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && out.isNotEmpty()) {
                out[out.lastIndex] = out.last() + raw.substring(1)
            } else {
                out += raw
            }
        }
        return out
    }

    internal fun escape(value: String): String = buildString(value.length + 4) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                ';' -> append("\\;")
                ',' -> append("\\,")
                '\n' -> append("\\n")
                '\r' -> Unit
                else -> append(ch)
            }
        }
    }

    internal fun unescape(value: String): String = buildString(value.length) {
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                val next = value[i + 1]
                append(if (next == 'n' || next == 'N') '\n' else next)
                i += 2
            } else {
                append(ch)
                i++
            }
        }
    }

    private fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }
}
