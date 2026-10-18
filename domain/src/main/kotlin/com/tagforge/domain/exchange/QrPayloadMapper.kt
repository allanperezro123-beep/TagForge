package com.tagforge.domain.exchange

import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.UriBuilders
import com.tagforge.domain.ndef.spec.VCard
import com.tagforge.domain.ndef.spec.VCardFields
import com.tagforge.domain.ndef.spec.WifiAuth
import com.tagforge.domain.ndef.spec.WifiEncryption
import java.io.ByteArrayOutputStream

/**
 * Maps the text a QR code carries to the record the wizard edits, and back for the kinds a QR can express (PR-02).
 *
 * Decoding never fails: anything not recognised, or recognised but unsafe to interpret (a Wi-Fi code without a name,
 * WEP or enterprise Wi-Fi, a geo URI with non-numeric coordinates), is kept as plain [RecordSpec.Text] so the user
 * sees exactly what the code said. Pure: the image decoding itself lives in the app module.
 */
object QrPayloadMapper {

    fun fromQr(raw: String): RecordSpec {
        val text = raw.trim()
        return when {
            text.startsWith("WIFI:", ignoreCase = true) -> wifi(text) ?: RecordSpec.Text(raw)
            text.startsWith("BEGIN:VCARD", ignoreCase = true) -> contact(text) ?: RecordSpec.Text(raw)
            text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true) -> RecordSpec.Uri(text)
            text.startsWith("tel:", ignoreCase = true) -> RecordSpec.Phone(text.substring(4).trim())
            text.startsWith("mailto:", ignoreCase = true) -> email(text)
            text.startsWith("smsto:", ignoreCase = true) -> smsTo(text) ?: RecordSpec.Text(raw)
            text.startsWith("sms:", ignoreCase = true) -> sms(text)
            text.startsWith("geo:", ignoreCase = true) -> geo(text) ?: RecordSpec.Text(raw)
            else -> RecordSpec.Text(raw)
        }
    }

    /** The QR text for [spec], or null when a QR code cannot honestly carry it. */
    fun toQr(spec: RecordSpec): String? = when (spec) {
        is RecordSpec.Text -> spec.text.takeIf { it.isNotEmpty() }
        is RecordSpec.Uri -> spec.uri.trim().takeIf { it.isNotEmpty() }
        is RecordSpec.Phone -> UriBuilders.tel(spec.number)
        is RecordSpec.Email -> UriBuilders.mailto(spec.to, spec.subject, spec.body)
        is RecordSpec.Sms -> UriBuilders.sms(spec.number, spec.body)
        is RecordSpec.Geo -> geoUri(spec)
        is RecordSpec.Contact -> spec.name.takeIf { it.isNotBlank() }?.let { VCard.build(spec.toFields()) }
        is RecordSpec.Wifi -> spec.ssid.takeIf { it.isNotEmpty() }?.let { wifiText(spec) }
        is RecordSpec.Bluetooth, is RecordSpec.AppLaunch, is RecordSpec.Mime, is RecordSpec.External -> null
    }

    /** A QR holds one payload: only a single-record message can be shown as one. */
    fun forMessage(records: List<RecordSpec>): String? = records.singleOrNull()?.let(::toQr)

    // ---- Wi-Fi -----------------------------------------------------------------------------------------------------

    private fun wifi(text: String): RecordSpec? {
        val fields = splitUnescaped(text.substring("WIFI:".length), ';').mapNotNull { part ->
            val pieces = splitUnescaped(part, ':', limit = 2)
            if (pieces.size == 2) pieces[0].trim().uppercase() to unescape(pieces[1]) else null
        }.toMap()
        val ssid = fields["S"].orEmpty()
        val password = fields["P"].orEmpty()
        if (ssid.isEmpty()) return null
        return when (fields["T"].orEmpty().uppercase()) {
            "NOPASS", "" -> if (password.isEmpty()) open(ssid) else secured(ssid, password)
            "WPA", "WPA2", "WPA3", "SAE" -> secured(ssid, password)
            else -> null
        }
    }

    private fun open(ssid: String) = RecordSpec.Wifi(ssid, "", WifiAuth.OPEN, WifiEncryption.NONE)

    private fun secured(ssid: String, password: String) = RecordSpec.Wifi(ssid, password, WifiAuth.WPA2_PSK, WifiEncryption.AES)

    private fun wifiText(wifi: RecordSpec.Wifi): String =
        if (wifi.auth == WifiAuth.OPEN) {
            "WIFI:T:nopass;S:${escape(wifi.ssid)};;"
        } else {
            "WIFI:T:WPA;S:${escape(wifi.ssid)};P:${escape(wifi.password)};;"
        }

    private fun escape(value: String): String = buildString(value.length + 4) {
        for (ch in value) {
            if (ch in WIFI_SPECIAL) append('\\')
            append(ch)
        }
    }

    private fun unescape(value: String): String = buildString(value.length) {
        var i = 0
        while (i < value.length) {
            if (value[i] == '\\' && i + 1 < value.length) i++
            append(value[i])
            i++
        }
    }

    /** Splits on [separator] unless it is escaped; the escapes stay in the pieces. At most [limit] pieces (0 = no limit). */
    private fun splitUnescaped(value: String, separator: Char, limit: Int = 0): List<String> {
        val pieces = ArrayList<String>()
        val current = StringBuilder()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            when {
                ch == '\\' && i + 1 < value.length -> current.append(ch).append(value[++i])
                ch == separator && (limit == 0 || pieces.size < limit - 1) -> {
                    pieces += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        pieces += current.toString()
        return pieces
    }

    // ---- other kinds -----------------------------------------------------------------------------------------------

    private fun contact(text: String): RecordSpec? = VCard.parse(text)?.let {
        RecordSpec.Contact(
            name = it.name,
            phone = it.phone.orEmpty(),
            email = it.email.orEmpty(),
            org = it.org.orEmpty(),
            url = it.url.orEmpty(),
            note = it.note.orEmpty(),
        )
    }

    private fun RecordSpec.Contact.toFields() = VCardFields(
        name = name,
        phone = phone.ifBlank { null },
        email = email.ifBlank { null },
        org = org.ifBlank { null },
        url = url.ifBlank { null },
        note = note.ifBlank { null },
    )

    private fun email(text: String): RecordSpec {
        val (address, query) = text.substring("mailto:".length).splitQuery()
        return RecordSpec.Email(percentDecode(address), query["subject"].orEmpty(), query["body"].orEmpty())
    }

    private fun sms(text: String): RecordSpec {
        val (number, query) = text.substring("sms:".length).splitQuery()
        return RecordSpec.Sms(percentDecode(number).trim(), query["body"].orEmpty())
    }

    /** The common `SMSTO:number:message` form. */
    private fun smsTo(text: String): RecordSpec? {
        val rest = text.substring("smsto:".length)
        val colon = rest.indexOf(':')
        return if (colon < 0) RecordSpec.Sms(rest.trim()) else RecordSpec.Sms(rest.substring(0, colon).trim(), rest.substring(colon + 1))
    }

    private fun geo(text: String): RecordSpec? {
        val (coordinates, query) = text.substring("geo:".length).splitQuery()
        val parts = coordinates.split(',')
        if (parts.size < 2) return null
        val latitude = parts[0].trim()
        val longitude = parts[1].trim()
        if (latitude.toDoubleOrNull() == null || longitude.toDoubleOrNull() == null) return null
        return RecordSpec.Geo(latitude, longitude, query["q"].orEmpty())
    }

    private fun geoUri(spec: RecordSpec.Geo): String? {
        val latitude = spec.latitude.trim().toDoubleOrNull() ?: return null
        val longitude = spec.longitude.trim().toDoubleOrNull() ?: return null
        return UriBuilders.geo(latitude, longitude, spec.query.trim())
    }

    // ---- URI helpers -----------------------------------------------------------------------------------------------

    private fun String.splitQuery(): Pair<String, Map<String, String>> {
        val mark = indexOf('?')
        if (mark < 0) return this to emptyMap()
        val query = substring(mark + 1).split('&').mapNotNull { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) null else pair.substring(0, eq).lowercase() to percentDecode(pair.substring(eq + 1))
        }.toMap()
        return substring(0, mark) to query
    }

    /** RFC 3986 percent-decoding of UTF-8; a `+` stays a plus (it is not a space in a URI). */
    private fun percentDecode(value: String): String {
        if ('%' !in value) return value
        val out = ByteArrayOutputStream(value.length)
        var i = 0
        while (i < value.length) {
            val hex = if (value[i] == '%' && i + 2 < value.length) value.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (hex != null) {
                out.write(hex)
                i += 3
            } else {
                // Copy up to the next '%' in one go so surrogate pairs reach the UTF-8 encoder whole.
                val next = value.indexOf('%', i + 1).let { if (it < 0) value.length else it }
                out.write(value.substring(i, next).toByteArray(Charsets.UTF_8))
                i = next
            }
        }
        return out.toString(Charsets.UTF_8)
    }

    private const val WIFI_SPECIAL = "\\;,:\""
}
