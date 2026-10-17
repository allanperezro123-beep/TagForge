package com.tagforge.app.feature.write

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.tagforge.app.R

@StringRes
fun RecordKind.titleRes(): Int = when (this) {
    RecordKind.TEXT -> R.string.write_type_text
    RecordKind.URL -> R.string.write_type_url
    RecordKind.EMAIL -> R.string.write_type_email
    RecordKind.PHONE -> R.string.write_type_phone
    RecordKind.SMS -> R.string.write_type_sms
    RecordKind.GEO -> R.string.write_type_geo
    RecordKind.CONTACT -> R.string.write_type_contact
    RecordKind.WIFI -> R.string.write_type_wifi
    RecordKind.BLUETOOTH -> R.string.write_type_bluetooth
    RecordKind.APP -> R.string.write_type_app
    RecordKind.MIME -> R.string.write_type_mime
    RecordKind.EXTERNAL -> R.string.write_type_external
}

@StringRes
fun RecordKind.descriptionRes(): Int = when (this) {
    RecordKind.TEXT -> R.string.write_type_text_desc
    RecordKind.URL -> R.string.write_type_url_desc
    RecordKind.EMAIL -> R.string.write_type_email_desc
    RecordKind.PHONE -> R.string.write_type_phone_desc
    RecordKind.SMS -> R.string.write_type_sms_desc
    RecordKind.GEO -> R.string.write_type_geo_desc
    RecordKind.CONTACT -> R.string.write_type_contact_desc
    RecordKind.WIFI -> R.string.write_type_wifi_desc
    RecordKind.BLUETOOTH -> R.string.write_type_bluetooth_desc
    RecordKind.APP -> R.string.write_type_app_desc
    RecordKind.MIME -> R.string.write_type_mime_desc
    RecordKind.EXTERNAL -> R.string.write_type_external_desc
}

fun RecordKind.icon(): ImageVector = when (this) {
    RecordKind.TEXT -> Icons.Outlined.TextFields
    RecordKind.URL -> Icons.Outlined.Link
    RecordKind.EMAIL -> Icons.Outlined.Email
    RecordKind.PHONE -> Icons.Outlined.Call
    RecordKind.SMS -> Icons.Outlined.Sms
    RecordKind.GEO -> Icons.Outlined.Place
    RecordKind.CONTACT -> Icons.Outlined.Contacts
    RecordKind.WIFI -> Icons.Outlined.Wifi
    RecordKind.BLUETOOTH -> Icons.Outlined.Bluetooth
    RecordKind.APP -> Icons.Outlined.Apps
    RecordKind.MIME -> Icons.Outlined.Description
    RecordKind.EXTERNAL -> Icons.Outlined.Extension
}

@StringRes
fun RecordGroup.titleRes(): Int = when (this) {
    RecordGroup.BASIC -> R.string.write_group_basic
    RecordGroup.COMMUNICATION -> R.string.write_group_communication
    RecordGroup.PEOPLE -> R.string.write_group_people
    RecordGroup.CONNECTIVITY -> R.string.write_group_connectivity
    RecordGroup.ADVANCED -> R.string.write_group_advanced
}
