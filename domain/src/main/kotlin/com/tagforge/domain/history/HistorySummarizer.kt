package com.tagforge.domain.history

import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.ParsedRecord
import com.tagforge.domain.ndef.RecordInterpreter
import com.tagforge.domain.ndef.spec.WifiWsc

/**
 * Builds the one-line description stored with a history entry, and strips credentials from the stored bytes.
 * The summary shows values only (no type labels) so the UI can localise around it.
 */
object HistorySummarizer {

    const val MAX_LENGTH = 80
    private const val SEPARATOR = " · "
    private const val ELLIPSIS = "…"
    private const val UNKNOWN = "?"
    private val WHITESPACE = Regex("\\s+")

    fun summarize(message: NdefMessage?): String {
        if (message == null || message.isEmpty) return ""
        val joined = RecordInterpreter.interpret(message).joinToString(SEPARATOR, transform = ::describe)
        return if (joined.length <= MAX_LENGTH) joined else joined.take(MAX_LENGTH - ELLIPSIS.length) + ELLIPSIS
    }

    /**
     * The same message with every WiFi network key removed. Works on the raw WSC TLV of any `application/vnd.wfa.wsc`
     * record, NOT on the decoded credentials: real tags carry auth/encryption codes this app does not model
     * (WPA/WPA2 mixed, WPA3-SAE, TKIP+AES) and a payload that fails to decode must never be stored as-is.
     * Everything else is returned untouched.
     */
    fun redact(message: NdefMessage): NdefMessage = NdefMessage(
        message.records.map { record ->
            if (WifiWsc.isWscRecord(record)) record.copy(payload = WifiWsc.redactNetworkKey(record.payload)) else record
        },
    )

    private fun describe(record: ParsedRecord): String = when (record) {
        is ParsedRecord.Text -> record.text.replace(WHITESPACE, " ").trim()
        is ParsedRecord.Uri -> record.uri
        is ParsedRecord.SmartPoster -> record.title ?: record.uri
        is ParsedRecord.Wifi -> record.credentials.ssid
        is ParsedRecord.Bluetooth -> record.address.name ?: record.address.mac
        is ParsedRecord.VCard -> record.fields.name
        is ParsedRecord.Aar -> record.packageName
        is ParsedRecord.Mime -> record.mimeType
        is ParsedRecord.External -> "${record.domain}:${record.type}"
        is ParsedRecord.Unknown -> UNKNOWN
    }
}
