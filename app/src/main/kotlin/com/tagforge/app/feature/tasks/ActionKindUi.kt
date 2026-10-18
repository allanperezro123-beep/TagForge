package com.tagforge.app.feature.tasks

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.RingVolume
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiPassword
import androidx.compose.ui.graphics.vector.ImageVector
import com.tagforge.app.R

@StringRes
fun ActionKind.titleRes(): Int = when (this) {
    ActionKind.RINGER -> R.string.action_kind_ringer
    ActionKind.VOLUME -> R.string.action_kind_volume
    ActionKind.BRIGHTNESS -> R.string.action_kind_brightness
    ActionKind.FLASHLIGHT -> R.string.action_kind_flashlight
    ActionKind.MEDIA -> R.string.action_kind_media
    ActionKind.WIFI_TOGGLE -> R.string.action_kind_wifi_toggle
    ActionKind.WIFI_JOIN -> R.string.action_kind_wifi_join
    ActionKind.BLUETOOTH_TOGGLE -> R.string.action_kind_bluetooth_toggle
    ActionKind.SETTINGS -> R.string.action_kind_settings
    ActionKind.OPEN_URL -> R.string.action_kind_open_url
    ActionKind.LAUNCH_APP -> R.string.action_kind_launch_app
    ActionKind.HTTP -> R.string.action_kind_http
    ActionKind.SMS -> R.string.action_kind_sms
    ActionKind.DIAL -> R.string.action_kind_dial
    ActionKind.NOTIFY -> R.string.action_kind_notify
    ActionKind.SPEAK -> R.string.action_kind_speak
    ActionKind.ALARM -> R.string.action_kind_alarm
    ActionKind.TIMER -> R.string.action_kind_timer
    ActionKind.CALENDAR -> R.string.action_kind_calendar
}

fun ActionKind.icon(): ImageVector = when (this) {
    ActionKind.RINGER -> Icons.Outlined.RingVolume
    ActionKind.VOLUME -> Icons.Outlined.VolumeUp
    ActionKind.BRIGHTNESS -> Icons.Outlined.BrightnessMedium
    ActionKind.FLASHLIGHT -> Icons.Outlined.FlashlightOn
    ActionKind.MEDIA -> Icons.Outlined.MusicNote
    ActionKind.WIFI_TOGGLE -> Icons.Outlined.Wifi
    ActionKind.WIFI_JOIN -> Icons.Outlined.WifiPassword
    ActionKind.BLUETOOTH_TOGGLE -> Icons.Outlined.Bluetooth
    ActionKind.SETTINGS -> Icons.Outlined.Settings
    ActionKind.OPEN_URL -> Icons.Outlined.Link
    ActionKind.LAUNCH_APP -> Icons.Outlined.Apps
    ActionKind.HTTP -> Icons.Outlined.CloudUpload
    ActionKind.SMS -> Icons.Outlined.Sms
    ActionKind.DIAL -> Icons.Outlined.Call
    ActionKind.NOTIFY -> Icons.Outlined.Notifications
    ActionKind.SPEAK -> Icons.Outlined.RecordVoiceOver
    ActionKind.ALARM -> Icons.Outlined.Alarm
    ActionKind.TIMER -> Icons.Outlined.Timer
    ActionKind.CALENDAR -> Icons.Outlined.EventNote
}

@StringRes
fun ActionGroup.titleRes(): Int = when (this) {
    ActionGroup.SOUND_AND_SCREEN -> R.string.builder_group_sound
    ActionGroup.CONNECTIVITY -> R.string.builder_group_connectivity
    ActionGroup.APPS_AND_LINKS -> R.string.builder_group_apps
    ActionGroup.COMMUNICATION -> R.string.builder_group_communication
    ActionGroup.TIME -> R.string.builder_group_time
}
