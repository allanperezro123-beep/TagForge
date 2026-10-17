package com.tagforge.domain.ndef.spec

import com.tagforge.domain.common.Hex

/** The form field an error belongs to, so the UI can show it right next to the control. */
enum class SpecField {
    TEXT, LANGUAGE, URI, TO, NUMBER, LATITUDE, LONGITUDE, NAME, PHONE, EMAIL, URL,
    SSID, PASSWORD, MAC, DEVICE_NAME, PACKAGE, MIME_TYPE, PAYLOAD, DOMAIN, TYPE,
}

/** Why a field is wrong. Codes only: the app maps them to es/en text. */
enum class SpecError {
    REQUIRED, INVALID_URL, INVALID_EMAIL, INVALID_PHONE, INVALID_LANGUAGE, NOT_A_NUMBER, OUT_OF_RANGE,
    SSID_TOO_LONG, PASSWORD_LENGTH, INVALID_MAC, TOO_LONG, INVALID_PACKAGE, INVALID_MIME_TYPE, INVALID_HEX,
    INVALID_EXTERNAL_TYPE,
}

data class FieldError(val field: SpecField, val error: SpecError)

object RecordSpecValidator {

    private const val MAX_SSID_BYTES = 32
    private const val MIN_WPA_KEY = 8
    private const val MAX_WPA_KEY = 63
    private const val RAW_PSK_HEX_LENGTH = 64
    private const val MAX_DEVICE_NAME_BYTES = 240
    private const val MIN_PHONE_DIGITS = 3
    private const val MAX_PHONE_DIGITS = 20
    private const val MAX_LATITUDE = 90.0
    private const val MAX_LONGITUDE = 180.0

    private val LANGUAGE = Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$")
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val PHONE = Regex("^\\+?[0-9 ()\\-.]+$")
    private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.\\-]*):(.*)$", RegexOption.DOT_MATCHES_ALL)
    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private val MIME = Regex("^[A-Za-z0-9][A-Za-z0-9!#$&^_.+\\-]*/[A-Za-z0-9][A-Za-z0-9!#$&^_.+\\-]*$")
    private val EXTERNAL_PART = Regex("^[^\\s:]+$")
    private val HEX_DIGITS = Regex("^[0-9A-Fa-f]+$")

    fun validate(spec: RecordSpec): List<FieldError> = buildList {
        when (spec) {
            is RecordSpec.Text -> {
                if (spec.text.isBlank()) add(SpecField.TEXT, SpecError.REQUIRED)
                if (!LANGUAGE.matches(spec.languageCode.trim())) add(SpecField.LANGUAGE, SpecError.INVALID_LANGUAGE)
            }
            is RecordSpec.Uri -> checkUrl(SpecField.URI, spec.uri, required = true)
            is RecordSpec.Email -> checkEmail(SpecField.TO, spec.to, required = true)
            is RecordSpec.Phone -> checkPhone(SpecField.NUMBER, spec.number, required = true)
            is RecordSpec.Sms -> checkPhone(SpecField.NUMBER, spec.number, required = true)
            is RecordSpec.Geo -> {
                checkCoordinate(SpecField.LATITUDE, spec.latitude, MAX_LATITUDE)
                checkCoordinate(SpecField.LONGITUDE, spec.longitude, MAX_LONGITUDE)
            }
            is RecordSpec.Contact -> {
                if (spec.name.isBlank()) add(SpecField.NAME, SpecError.REQUIRED)
                checkPhone(SpecField.PHONE, spec.phone, required = false)
                checkEmail(SpecField.EMAIL, spec.email, required = false)
                checkUrl(SpecField.URL, spec.url, required = false)
            }
            is RecordSpec.Wifi -> checkWifi(spec)
            is RecordSpec.Bluetooth -> {
                when {
                    spec.mac.isBlank() -> add(SpecField.MAC, SpecError.REQUIRED)
                    !BluetoothOob.isValidMac(spec.mac) -> add(SpecField.MAC, SpecError.INVALID_MAC)
                }
                if (spec.name.toByteArray(Charsets.UTF_8).size > MAX_DEVICE_NAME_BYTES) add(SpecField.DEVICE_NAME, SpecError.TOO_LONG)
            }
            is RecordSpec.AppLaunch -> when {
                spec.packageName.isBlank() -> add(SpecField.PACKAGE, SpecError.REQUIRED)
                !PACKAGE.matches(spec.packageName.trim()) -> add(SpecField.PACKAGE, SpecError.INVALID_PACKAGE)
            }
            is RecordSpec.Mime -> {
                when {
                    spec.mimeType.isBlank() -> add(SpecField.MIME_TYPE, SpecError.REQUIRED)
                    !MIME.matches(spec.mimeType.trim()) -> add(SpecField.MIME_TYPE, SpecError.INVALID_MIME_TYPE)
                }
                if (spec.payloadText.isEmpty()) add(SpecField.PAYLOAD, SpecError.REQUIRED)
            }
            is RecordSpec.External -> {
                checkExternalPart(SpecField.DOMAIN, spec.domain)
                checkExternalPart(SpecField.TYPE, spec.type)
                if (Hex.parse(spec.payloadHex) == null) add(SpecField.PAYLOAD, SpecError.INVALID_HEX)
            }
        }
    }

    /** Errors per record index; records without errors are absent from the map. */
    fun validateAll(specs: List<RecordSpec>): Map<Int, List<FieldError>> =
        specs.withIndex().mapNotNull { (index, spec) -> validate(spec).takeIf { it.isNotEmpty() }?.let { index to it } }.toMap()

    fun isValid(spec: RecordSpec): Boolean = validate(spec).isEmpty()

    // ---- field rules -------------------------------------------------------------------------------

    private fun MutableList<FieldError>.add(field: SpecField, error: SpecError) {
        add(FieldError(field, error))
    }

    private fun MutableList<FieldError>.checkUrl(field: SpecField, value: String, required: Boolean) {
        val text = value.trim()
        if (text.isEmpty()) {
            if (required) add(field, SpecError.REQUIRED)
            return
        }
        val match = SCHEME.matchEntire(text)
        val valid = match != null && text.none { it.isWhitespace() } && run {
            val scheme = match.groupValues[1].lowercase()
            val rest = match.groupValues[2]
            if (scheme == "http" || scheme == "https") {
                rest.startsWith("//") && rest.removePrefix("//").takeWhile { it != '/' && it != '?' && it != '#' }.isNotEmpty()
            } else {
                rest.isNotEmpty()
            }
        }
        if (!valid) add(field, SpecError.INVALID_URL)
    }

    private fun MutableList<FieldError>.checkEmail(field: SpecField, value: String, required: Boolean) {
        val text = value.trim()
        when {
            text.isEmpty() -> if (required) add(field, SpecError.REQUIRED)
            !EMAIL.matches(text) -> add(field, SpecError.INVALID_EMAIL)
        }
    }

    private fun MutableList<FieldError>.checkPhone(field: SpecField, value: String, required: Boolean) {
        val text = value.trim()
        if (text.isEmpty()) {
            if (required) add(field, SpecError.REQUIRED)
            return
        }
        val digits = text.count { it in '0'..'9' }
        if (!PHONE.matches(text) || digits !in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS) add(field, SpecError.INVALID_PHONE)
    }

    private fun MutableList<FieldError>.checkCoordinate(field: SpecField, value: String, limit: Double) {
        val text = value.trim()
        if (text.isEmpty()) {
            add(field, SpecError.REQUIRED)
            return
        }
        val number = text.toDoubleOrNull()?.takeIf { it.isFinite() }
        when {
            number == null -> add(field, SpecError.NOT_A_NUMBER)
            number < -limit || number > limit -> add(field, SpecError.OUT_OF_RANGE)
        }
    }

    private fun MutableList<FieldError>.checkWifi(spec: RecordSpec.Wifi) {
        when {
            spec.ssid.isEmpty() -> add(SpecField.SSID, SpecError.REQUIRED)
            spec.ssid.toByteArray(Charsets.UTF_8).size > MAX_SSID_BYTES -> add(SpecField.SSID, SpecError.SSID_TOO_LONG)
        }
        if (spec.auth == WifiAuth.OPEN) return
        val key = spec.password
        val rawPsk = key.length == RAW_PSK_HEX_LENGTH && HEX_DIGITS.matches(key)
        when {
            key.isEmpty() -> add(SpecField.PASSWORD, SpecError.REQUIRED)
            key.length !in MIN_WPA_KEY..MAX_WPA_KEY && !rawPsk -> add(SpecField.PASSWORD, SpecError.PASSWORD_LENGTH)
        }
    }

    private fun MutableList<FieldError>.checkExternalPart(field: SpecField, value: String) {
        val text = value.trim()
        when {
            text.isEmpty() -> add(field, SpecError.REQUIRED)
            !EXTERNAL_PART.matches(text) || text.any { it.code > 0x7F } -> add(field, SpecError.INVALID_EXTERNAL_TYPE)
        }
    }
}
