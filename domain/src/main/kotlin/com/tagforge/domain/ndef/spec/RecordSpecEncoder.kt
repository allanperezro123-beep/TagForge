package com.tagforge.domain.ndef.spec

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Hex
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import kotlinx.serialization.json.Json

/** Turns what the user typed into NDEF records. Invalid input yields null, never an exception. */
object RecordSpecEncoder {

    fun encode(spec: RecordSpec): NdefRecord? {
        if (!RecordSpecValidator.isValid(spec)) return null
        return when (spec) {
            is RecordSpec.Text -> NdefRecord.text(spec.text, spec.languageCode.trim())
            is RecordSpec.Uri -> NdefRecord.uri(spec.uri.trim())
            is RecordSpec.Email -> NdefRecord.uri(UriBuilders.mailto(spec.to, spec.subject, spec.body))
            is RecordSpec.Phone -> NdefRecord.uri(UriBuilders.tel(spec.number))
            is RecordSpec.Sms -> NdefRecord.uri(UriBuilders.sms(spec.number, spec.body))
            is RecordSpec.Geo -> NdefRecord.uri(
                UriBuilders.geo(spec.latitude.trim().toDouble(), spec.longitude.trim().toDouble(), spec.query.trim()),
            )
            is RecordSpec.Contact -> NdefRecord.vcard(
                VCard.build(
                    VCardFields(
                        name = spec.name.trim(),
                        phone = spec.phone.trim().ifEmpty { null },
                        email = spec.email.trim().ifEmpty { null },
                        org = spec.org.trim().ifEmpty { null },
                        url = spec.url.trim().ifEmpty { null },
                        note = spec.note.ifBlank { null },
                    ),
                ),
            )
            is RecordSpec.Wifi -> WifiWsc.record(wifiCredentials(spec))
            is RecordSpec.Bluetooth -> BluetoothOob.record(spec.mac, spec.name.ifEmpty { null })
            is RecordSpec.AppLaunch -> NdefRecord.aar(spec.packageName.trim())
            is RecordSpec.Mime -> NdefRecord.mime(spec.mimeType.trim(), Bytes.copyOf(spec.payloadText.toByteArray(Charsets.UTF_8)))
            is RecordSpec.External -> NdefRecord.external(
                spec.domain.trim(),
                spec.type.trim(),
                Hex.parse(spec.payloadHex) ?: Bytes.EMPTY,
            )
        }
    }

    /**
     * The whole message, in order, with an optional AAR for [aarPackage] last (AAR must be the final record).
     * Null when any record is invalid or there are no records.
     */
    fun encodeMessage(specs: List<RecordSpec>, aarPackage: String?): NdefMessage? {
        if (specs.isEmpty()) return null
        val records = specs.map { encode(it) ?: return null }
        return NdefMessage(if (aarPackage == null) records else records + NdefRecord.aar(aarPackage))
    }

    /** Open networks never carry a key or an encryption; secured ones never claim "no encryption". */
    private fun wifiCredentials(spec: RecordSpec.Wifi): WifiCredentials = when (spec.auth) {
        WifiAuth.OPEN -> WifiCredentials(spec.ssid, null, WifiAuth.OPEN, WifiEncryption.NONE)
        else -> WifiCredentials(
            spec.ssid,
            spec.password,
            spec.auth,
            spec.encryption.takeUnless { it == WifiEncryption.NONE } ?: WifiEncryption.AES,
        )
    }
}

/** Templates persist their records as JSON. Unknown fields are ignored; anything unreadable decodes to null. */
object RecordSpecJson {

    private val json = Json {
        classDiscriminator = "t"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(specs: List<RecordSpec>): String = json.encodeToString(RECORDS_SERIALIZER, specs)

    fun decode(text: String): List<RecordSpec>? =
        runCatching { json.decodeFromString(RECORDS_SERIALIZER, text) }.getOrNull()

    private val RECORDS_SERIALIZER = kotlinx.serialization.builtins.ListSerializer(RecordSpec.serializer())
}
