package com.tagforge.domain.ndef.spec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the user wants on a tag, one record at a time, exactly as typed in the wizard. Every field is a String (even
 * coordinates) so a half-typed form is representable; [RecordSpecValidator] says whether it is complete and
 * [RecordSpecEncoder] turns a valid one into an NDEF record. The `@SerialName` codes are persisted in templates:
 * never rename them.
 */
@Serializable
sealed interface RecordSpec {

    @Serializable
    @SerialName("text")
    data class Text(val text: String = "", val languageCode: String = "en") : RecordSpec

    @Serializable
    @SerialName("uri")
    data class Uri(val uri: String = "") : RecordSpec

    @Serializable
    @SerialName("email")
    data class Email(val to: String = "", val subject: String = "", val body: String = "") : RecordSpec

    @Serializable
    @SerialName("phone")
    data class Phone(val number: String = "") : RecordSpec

    @Serializable
    @SerialName("sms")
    data class Sms(val number: String = "", val body: String = "") : RecordSpec

    @Serializable
    @SerialName("geo")
    data class Geo(val latitude: String = "", val longitude: String = "", val query: String = "") : RecordSpec

    @Serializable
    @SerialName("contact")
    data class Contact(
        val name: String = "",
        val phone: String = "",
        val email: String = "",
        val org: String = "",
        val url: String = "",
        val note: String = "",
    ) : RecordSpec

    @Serializable
    @SerialName("wifi")
    data class Wifi(
        val ssid: String = "",
        val password: String = "",
        val auth: WifiAuth = WifiAuth.WPA2_PSK,
        val encryption: WifiEncryption = WifiEncryption.AES,
    ) : RecordSpec

    @Serializable
    @SerialName("bluetooth")
    data class Bluetooth(val mac: String = "", val name: String = "") : RecordSpec

    @Serializable
    @SerialName("app")
    data class AppLaunch(val packageName: String = "") : RecordSpec

    @Serializable
    @SerialName("mime")
    data class Mime(val mimeType: String = "", val payloadText: String = "") : RecordSpec

    @Serializable
    @SerialName("external")
    data class External(val domain: String = "", val type: String = "", val payloadHex: String = "") : RecordSpec
}
