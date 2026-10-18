package com.tagforge.domain.exchange

import com.tagforge.domain.history.HistoryEntry

/**
 * History as CSV (HT-04): RFC 4180 (comma separated, CRLF rows, quotes doubled) and UTF-8.
 * Tag content is untrusted and spreadsheets run formulas, so a cell that starts with `=`, `+`, `-` (followed by a
 * non-space) or `@` gets a leading apostrophe. The raw NDEF bytes are left out: the CSV is for reading, JSON is the backup.
 */
object CsvExporter {

    private const val HEADER = "id,time,kind,outcome,uid,tag_type,summary"
    private const val EOL = "\r\n"
    private val FORMULA_STARTERS = setOf('=', '+', '@', '\t', '\r')

    fun export(entries: List<HistoryEntry>): String = buildString {
        append(HEADER).append(EOL)
        for (entry in entries) {
            append(
                listOf(
                    entry.id.toString(),
                    entry.at.toString(),
                    entry.kind.code,
                    entry.outcome.code,
                    entry.uid?.toHex().orEmpty(),
                    entry.tagType.orEmpty(),
                    entry.summary,
                ).joinToString(",") { cell(it) },
            ).append(EOL)
        }
    }

    fun toUtf8(csv: String): ByteArray = csv.toByteArray(Charsets.UTF_8)

    private fun cell(raw: String): String {
        val text = if (isFormula(raw)) "'$raw" else raw
        val mustQuote = text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (mustQuote) "\"" + text.replace("\"", "\"\"") + "\"" else text
    }

    private fun isFormula(text: String): Boolean {
        val first = text.firstOrNull() ?: return false
        if (first in FORMULA_STARTERS) return true
        return first == '-' && text.length > 1 && !text[1].isWhitespace()
    }
}
