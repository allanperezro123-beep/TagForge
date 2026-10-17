package com.tagforge.app.feature.write

import com.tagforge.domain.ndef.spec.RecordSpec

enum class RecordGroup { BASIC, COMMUNICATION, PEOPLE, CONNECTIVITY, ADVANCED }

/** The record types the wizard offers, in picker order. One-to-one with the [RecordSpec] variants. */
enum class RecordKind(val group: RecordGroup) {
    TEXT(RecordGroup.BASIC),
    URL(RecordGroup.BASIC),
    EMAIL(RecordGroup.COMMUNICATION),
    PHONE(RecordGroup.COMMUNICATION),
    SMS(RecordGroup.COMMUNICATION),
    GEO(RecordGroup.PEOPLE),
    CONTACT(RecordGroup.PEOPLE),
    WIFI(RecordGroup.CONNECTIVITY),
    BLUETOOTH(RecordGroup.CONNECTIVITY),
    APP(RecordGroup.ADVANCED),
    MIME(RecordGroup.ADVANCED),
    EXTERNAL(RecordGroup.ADVANCED),
}

/** An empty form of this kind. */
fun RecordKind.newSpec(): RecordSpec = when (this) {
    RecordKind.TEXT -> RecordSpec.Text()
    RecordKind.URL -> RecordSpec.Uri()
    RecordKind.EMAIL -> RecordSpec.Email()
    RecordKind.PHONE -> RecordSpec.Phone()
    RecordKind.SMS -> RecordSpec.Sms()
    RecordKind.GEO -> RecordSpec.Geo()
    RecordKind.CONTACT -> RecordSpec.Contact()
    RecordKind.WIFI -> RecordSpec.Wifi()
    RecordKind.BLUETOOTH -> RecordSpec.Bluetooth()
    RecordKind.APP -> RecordSpec.AppLaunch()
    RecordKind.MIME -> RecordSpec.Mime()
    RecordKind.EXTERNAL -> RecordSpec.External()
}

fun RecordSpec.kind(): RecordKind = when (this) {
    is RecordSpec.Text -> RecordKind.TEXT
    is RecordSpec.Uri -> RecordKind.URL
    is RecordSpec.Email -> RecordKind.EMAIL
    is RecordSpec.Phone -> RecordKind.PHONE
    is RecordSpec.Sms -> RecordKind.SMS
    is RecordSpec.Geo -> RecordKind.GEO
    is RecordSpec.Contact -> RecordKind.CONTACT
    is RecordSpec.Wifi -> RecordKind.WIFI
    is RecordSpec.Bluetooth -> RecordKind.BLUETOOTH
    is RecordSpec.AppLaunch -> RecordKind.APP
    is RecordSpec.Mime -> RecordKind.MIME
    is RecordSpec.External -> RecordKind.EXTERNAL
}
