package com.tagforge.domain.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One step of a task (TK-01). Parameters are typed; free text stays Strings. Persisted as JSON (`t` discriminator) in
 * Room and as compact binary on tags ([TaskCodec]). Every enum entry has a stable `code` that is the byte value on a
 * tag AND the serial name in JSON: never renumber, only add.
 */
@Serializable
sealed interface TaskAction {

    @Serializable @SerialName("ringer")
    data class SetRinger(val mode: RingerMode) : TaskAction

    /** [level] is a percentage, 0..100. */
    @Serializable @SerialName("volume")
    data class SetVolume(val stream: VolumeStream, val level: Int) : TaskAction

    /** [level] is 0..255, the raw system brightness range. */
    @Serializable @SerialName("brightness")
    data class SetBrightness(val level: Int) : TaskAction

    @Serializable @SerialName("wifi_toggle")
    data class WifiToggle(val enabled: Boolean) : TaskAction

    @Serializable @SerialName("wifi_join")
    data class WifiJoin(val ssid: String, val key: String = "", val auth: JoinAuth = JoinAuth.WPA2) : TaskAction

    @Serializable @SerialName("bluetooth_toggle")
    data class BluetoothToggle(val enabled: Boolean) : TaskAction

    @Serializable @SerialName("open_settings")
    data class OpenSettings(val screen: SettingsTarget) : TaskAction

    @Serializable @SerialName("open_url")
    data class OpenUrl(val url: String) : TaskAction

    @Serializable @SerialName("launch_app")
    data class LaunchApp(val packageName: String) : TaskAction

    @Serializable @SerialName("alarm")
    data class Alarm(val hour: Int, val minute: Int, val label: String = "") : TaskAction

    @Serializable @SerialName("timer")
    data class Timer(val seconds: Int, val label: String = "") : TaskAction

    /** Opens the calendar's "new event" screen prefilled; [durationMinutes] starts from "now". */
    @Serializable @SerialName("calendar")
    data class CalendarEvent(val title: String, val durationMinutes: Int = 60) : TaskAction

    @Serializable @SerialName("sms")
    data class SmsCompose(val number: String, val body: String = "") : TaskAction

    @Serializable @SerialName("dial")
    data class Dial(val number: String) : TaskAction

    @Serializable @SerialName("speak")
    data class Speak(val text: String) : TaskAction

    @Serializable @SerialName("flashlight")
    data class Flashlight(val on: Boolean) : TaskAction

    @Serializable @SerialName("notify")
    data class Notify(val title: String, val text: String = "") : TaskAction

    /** No headers and no body: a tag can only ask for a plain GET or an empty POST. */
    @Serializable @SerialName("http")
    data class HttpRequest(val url: String, val method: HttpMethod = HttpMethod.GET) : TaskAction

    @Serializable @SerialName("media")
    data class MediaKey(val key: MediaButton) : TaskAction
}

@Serializable
enum class RingerMode(val code: Int) {
    @SerialName("silent") SILENT(0),
    @SerialName("vibrate") VIBRATE(1),
    @SerialName("normal") NORMAL(2),
    ;

    companion object {
        fun fromCode(code: Int): RingerMode? = entries.firstOrNull { it.code == code }
    }
}

@Serializable
enum class VolumeStream(val code: Int) {
    @SerialName("media") MEDIA(0),
    @SerialName("ring") RING(1),
    @SerialName("alarm") ALARM(2),
    @SerialName("notification") NOTIFICATION(3),
    ;

    companion object {
        fun fromCode(code: Int): VolumeStream? = entries.firstOrNull { it.code == code }
    }
}

@Serializable
enum class JoinAuth(val code: Int) {
    @SerialName("open") OPEN(0),
    @SerialName("wpa2") WPA2(1),
    @SerialName("wpa3") WPA3(2),
    ;

    companion object {
        fun fromCode(code: Int): JoinAuth? = entries.firstOrNull { it.code == code }
    }
}

@Serializable
enum class HttpMethod(val code: Int) {
    @SerialName("get") GET(0),
    @SerialName("post") POST(1),
    ;

    companion object {
        fun fromCode(code: Int): HttpMethod? = entries.firstOrNull { it.code == code }
    }
}

@Serializable
enum class MediaButton(val code: Int) {
    @SerialName("play_pause") PLAY_PAUSE(0),
    @SerialName("next") NEXT(1),
    @SerialName("previous") PREVIOUS(2),
    ;

    companion object {
        fun fromCode(code: Int): MediaButton? = entries.firstOrNull { it.code == code }
    }
}

/** Where [TaskAction.OpenSettings] goes. Mobile data, airplane, hotspot, GPS and NFC cannot be flipped by an app: they open their screen. */
@Serializable
enum class SettingsTarget(val code: Int) {
    @SerialName("wifi") WIFI(0),
    @SerialName("bluetooth") BLUETOOTH(1),
    @SerialName("nfc") NFC(2),
    @SerialName("location") LOCATION(3),
    @SerialName("airplane") AIRPLANE(4),
    @SerialName("mobile_data") MOBILE_DATA(5),
    @SerialName("hotspot") HOTSPOT(6),
    @SerialName("internet_panel") INTERNET_PANEL(7),
    @SerialName("volume") VOLUME(8),
    @SerialName("dnd") DND(9),
    ;

    companion object {
        fun fromCode(code: Int): SettingsTarget? = entries.firstOrNull { it.code == code }
    }
}

/** The "this one can hurt" badge shown on the confirmation sheet (TK-06). [NONE] means no badge. */
enum class TaskRisk {
    NONE,
    HTTP,
    SMS,
    CALL,
    LINK,
    APP,
    WIFI,
    ;

    val isElevated: Boolean get() = this != NONE
}

/** The elevated-risk set of TK-06: HttpRequest, Sms, Dial, OpenUrl, LaunchApp, WifiJoin. */
val TaskAction.risk: TaskRisk
    get() = when (this) {
        is TaskAction.HttpRequest -> TaskRisk.HTTP
        is TaskAction.SmsCompose -> TaskRisk.SMS
        is TaskAction.Dial -> TaskRisk.CALL
        is TaskAction.OpenUrl -> TaskRisk.LINK
        is TaskAction.LaunchApp -> TaskRisk.APP
        is TaskAction.WifiJoin -> TaskRisk.WIFI
        else -> TaskRisk.NONE
    }
