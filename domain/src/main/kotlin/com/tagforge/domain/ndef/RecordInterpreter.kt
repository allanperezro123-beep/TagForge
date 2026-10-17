package com.tagforge.domain.ndef

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ndef.spec.BluetoothOob
import com.tagforge.domain.ndef.spec.SmartPoster
import com.tagforge.domain.ndef.spec.VCard
import com.tagforge.domain.ndef.spec.WifiWsc

/** Turns raw [NdefRecord]s into [ParsedRecord]s. Never throws: anything unrecognised becomes Unknown. */
object RecordInterpreter {

    fun interpret(message: NdefMessage): List<ParsedRecord> = message.records.map(::interpret)

    fun interpret(record: NdefRecord): ParsedRecord = when (record.tnf) {
        Tnf.WELL_KNOWN -> when (record.type) {
            NdefRecord.TYPE_TEXT -> decodeText(record) ?: ParsedRecord.Unknown(record)
            NdefRecord.TYPE_URI -> decodeUri(record) ?: ParsedRecord.Unknown(record)
            SmartPoster.TYPE -> SmartPoster.parse(record)?.let { ParsedRecord.SmartPoster(record, it.uri, it.title) }
                ?: ParsedRecord.Unknown(record)
            else -> ParsedRecord.Unknown(record)
        }
        Tnf.MIME -> decodeMime(record)
        Tnf.ABSOLUTE_URI -> ParsedRecord.Uri(record, record.type.asText())
        Tnf.EXTERNAL -> decodeExternal(record)
        else -> ParsedRecord.Unknown(record)
    }

    private fun decodeText(record: NdefRecord): ParsedRecord.Text? {
        val payload = record.payload
        if (payload.isEmpty()) return null
        val status = payload[0]
        val utf16 = status and 0x80 != 0
        val languageLength = status and 0x3F
        if (1 + languageLength > payload.size) return null

        val language = payload.slice(1, 1 + languageLength).asText(Charsets.US_ASCII)
        val body = payload.slice(1 + languageLength)
        val text = if (utf16) decodeUtf16(body) else body.asText()
        return ParsedRecord.Text(record, language, text)
    }

    /** UTF-16 as mandated by the Text RTD: BOM decides endianness, big-endian when absent. */
    private fun decodeUtf16(body: Bytes): String {
        if (body.size >= 2 && body[0] == 0xFF && body[1] == 0xFE) {
            return String(body.slice(2).toByteArray(), Charsets.UTF_16LE)
        }
        if (body.size >= 2 && body[0] == 0xFE && body[1] == 0xFF) {
            return String(body.slice(2).toByteArray(), Charsets.UTF_16BE)
        }
        return String(body.toByteArray(), Charsets.UTF_16BE)
    }

    private fun decodeUri(record: NdefRecord): ParsedRecord.Uri? {
        val payload = record.payload
        if (payload.isEmpty()) return null
        val rest = payload.slice(1).asText()
        val prefix = UriPrefixes.prefixFor(payload[0])
        return if (prefix != null) ParsedRecord.Uri(record, prefix + rest) else ParsedRecord.Uri(record, rest, prefixKnown = false)
    }

    /** Rich decoders apply only when the payload really parses; otherwise the record stays a generic MIME card. */
    private fun decodeMime(record: NdefRecord): ParsedRecord {
        val mime = record.type.asText()
        val generic = ParsedRecord.Mime(record, mime, record.payload)
        return when (mime.lowercase()) {
            WifiWsc.MIME_TYPE -> WifiWsc.parse(record.payload)?.let { ParsedRecord.Wifi(record, it) }
            BluetoothOob.MIME_TYPE -> BluetoothOob.parse(record.payload)?.let { ParsedRecord.Bluetooth(record, it) }
            NdefRecord.MIME_VCARD -> VCard.parse(record.payload.asText())?.let { ParsedRecord.VCard(record, it) }
            else -> null
        } ?: generic
    }

    private fun decodeExternal(record: NdefRecord): ParsedRecord {
        val name = record.type.asText()
        val colon = name.indexOf(':')
        if (colon <= 0) return ParsedRecord.Unknown(record)
        val domain = name.substring(0, colon)
        val type = name.substring(colon + 1)
        if (domain == NdefRecord.AAR_DOMAIN && type == NdefRecord.AAR_TYPE) {
            return ParsedRecord.Aar(record, record.payload.asText())
        }
        return ParsedRecord.External(record, domain, type, record.payload)
    }

    private fun Bytes.asText(charset: java.nio.charset.Charset = Charsets.UTF_8): String = String(toByteArray(), charset)
}
