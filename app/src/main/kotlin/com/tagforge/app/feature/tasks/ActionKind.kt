package com.tagforge.app.feature.tasks

import com.tagforge.domain.task.HttpMethod
import com.tagforge.domain.task.JoinAuth
import com.tagforge.domain.task.MediaButton
import com.tagforge.domain.task.RingerMode
import com.tagforge.domain.task.SettingsTarget
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.task.VolumeStream

/** Where an action sits in the picker. */
enum class ActionGroup { SOUND_AND_SCREEN, CONNECTIVITY, APPS_AND_LINKS, COMMUNICATION, TIME }

/**
 * The 19 things a task can do, as the picker offers them. [newAction] is the starting point of the form: sensible
 * defaults for switches and choices, empty text for what the user must type.
 */
enum class ActionKind(val group: ActionGroup) {
    RINGER(ActionGroup.SOUND_AND_SCREEN),
    VOLUME(ActionGroup.SOUND_AND_SCREEN),
    BRIGHTNESS(ActionGroup.SOUND_AND_SCREEN),
    FLASHLIGHT(ActionGroup.SOUND_AND_SCREEN),
    MEDIA(ActionGroup.SOUND_AND_SCREEN),
    WIFI_TOGGLE(ActionGroup.CONNECTIVITY),
    WIFI_JOIN(ActionGroup.CONNECTIVITY),
    BLUETOOTH_TOGGLE(ActionGroup.CONNECTIVITY),
    SETTINGS(ActionGroup.CONNECTIVITY),
    OPEN_URL(ActionGroup.APPS_AND_LINKS),
    LAUNCH_APP(ActionGroup.APPS_AND_LINKS),
    HTTP(ActionGroup.APPS_AND_LINKS),
    SMS(ActionGroup.COMMUNICATION),
    DIAL(ActionGroup.COMMUNICATION),
    NOTIFY(ActionGroup.COMMUNICATION),
    SPEAK(ActionGroup.COMMUNICATION),
    ALARM(ActionGroup.TIME),
    TIMER(ActionGroup.TIME),
    CALENDAR(ActionGroup.TIME),
    ;

    fun newAction(): TaskAction = when (this) {
        RINGER -> TaskAction.SetRinger(RingerMode.VIBRATE)
        VOLUME -> TaskAction.SetVolume(VolumeStream.MEDIA, DEFAULT_VOLUME)
        BRIGHTNESS -> TaskAction.SetBrightness(DEFAULT_BRIGHTNESS)
        FLASHLIGHT -> TaskAction.Flashlight(true)
        MEDIA -> TaskAction.MediaKey(MediaButton.PLAY_PAUSE)
        WIFI_TOGGLE -> TaskAction.WifiToggle(true)
        WIFI_JOIN -> TaskAction.WifiJoin("", "", JoinAuth.WPA2)
        BLUETOOTH_TOGGLE -> TaskAction.BluetoothToggle(true)
        SETTINGS -> TaskAction.OpenSettings(SettingsTarget.WIFI)
        OPEN_URL -> TaskAction.OpenUrl("https://")
        LAUNCH_APP -> TaskAction.LaunchApp("")
        HTTP -> TaskAction.HttpRequest("https://", HttpMethod.GET)
        SMS -> TaskAction.SmsCompose("", "")
        DIAL -> TaskAction.Dial("")
        NOTIFY -> TaskAction.Notify("", "")
        SPEAK -> TaskAction.Speak("")
        ALARM -> TaskAction.Alarm(DEFAULT_ALARM_HOUR, 0, "")
        TIMER -> TaskAction.Timer(DEFAULT_TIMER_SECONDS, "")
        CALENDAR -> TaskAction.CalendarEvent("", DEFAULT_EVENT_MINUTES)
    }

    companion object {
        private const val DEFAULT_VOLUME = 50
        private const val DEFAULT_BRIGHTNESS = 128
        private const val DEFAULT_ALARM_HOUR = 7
        private const val DEFAULT_TIMER_SECONDS = 60
        private const val DEFAULT_EVENT_MINUTES = 60

        fun of(action: TaskAction): ActionKind = when (action) {
            is TaskAction.SetRinger -> RINGER
            is TaskAction.SetVolume -> VOLUME
            is TaskAction.SetBrightness -> BRIGHTNESS
            is TaskAction.Flashlight -> FLASHLIGHT
            is TaskAction.MediaKey -> MEDIA
            is TaskAction.WifiToggle -> WIFI_TOGGLE
            is TaskAction.WifiJoin -> WIFI_JOIN
            is TaskAction.BluetoothToggle -> BLUETOOTH_TOGGLE
            is TaskAction.OpenSettings -> SETTINGS
            is TaskAction.OpenUrl -> OPEN_URL
            is TaskAction.LaunchApp -> LAUNCH_APP
            is TaskAction.HttpRequest -> HTTP
            is TaskAction.SmsCompose -> SMS
            is TaskAction.Dial -> DIAL
            is TaskAction.Notify -> NOTIFY
            is TaskAction.Speak -> SPEAK
            is TaskAction.Alarm -> ALARM
            is TaskAction.Timer -> TIMER
            is TaskAction.CalendarEvent -> CALENDAR
        }
    }
}
