package com.tagforge.app.ui.text

import android.content.res.Resources
import androidx.annotation.StringRes
import com.tagforge.app.R
import com.tagforge.app.feature.settings.PermissionKind
import com.tagforge.app.feature.settings.titleRes
import com.tagforge.domain.task.ActionOutcome
import com.tagforge.domain.task.BlockReason
import com.tagforge.domain.task.Capability
import com.tagforge.domain.task.FailReason
import com.tagforge.domain.task.FallbackReason
import com.tagforge.domain.task.JoinAuth
import com.tagforge.domain.task.MediaButton
import com.tagforge.domain.task.RingerMode
import com.tagforge.domain.task.SettingsTarget
import com.tagforge.domain.task.SkipReason
import com.tagforge.domain.task.SpecialGrant
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.task.TaskError
import com.tagforge.domain.task.TaskRisk
import com.tagforge.domain.task.VolumeStream
import kotlin.math.roundToInt

/**
 * Plain-language wording for tasks. The domain returns codes; every sentence the user reads about a task comes
 * from here, in both languages, with the full parameters (URL, number, SMS body, package, method and URL).
 */
object TaskText {

    private const val BRIGHTNESS_MAX = 255f
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600

    /** One line describing what [action] will do, parameters included. */
    fun describe(resources: Resources, action: TaskAction): String = when (action) {
        is TaskAction.SetRinger -> resources.getString(R.string.task_do_ringer, resources.getString(ringerRes(action.mode)))
        is TaskAction.SetVolume -> resources.getString(R.string.task_do_volume, resources.getString(streamRes(action.stream)), action.level)
        is TaskAction.SetBrightness -> resources.getString(R.string.task_do_brightness, (action.level / BRIGHTNESS_MAX * 100).roundToInt())
        is TaskAction.WifiToggle -> resources.getString(if (action.enabled) R.string.task_do_wifi_on else R.string.task_do_wifi_off)
        is TaskAction.WifiJoin -> resources.getString(R.string.task_do_wifi_join, action.ssid, resources.getString(joinAuthRes(action.auth)))
        is TaskAction.BluetoothToggle -> resources.getString(if (action.enabled) R.string.task_do_bluetooth_on else R.string.task_do_bluetooth_off)
        is TaskAction.OpenSettings -> resources.getString(R.string.task_do_settings, resources.getString(settingsRes(action.screen)))
        is TaskAction.OpenUrl -> resources.getString(R.string.task_do_open_url, action.url)
        is TaskAction.LaunchApp -> resources.getString(R.string.task_do_launch_app, action.packageName)
        is TaskAction.Alarm ->
            if (action.label.isBlank()) {
                resources.getString(R.string.task_do_alarm, time(action.hour, action.minute))
            } else {
                resources.getString(R.string.task_do_alarm_labeled, time(action.hour, action.minute), action.label)
            }
        is TaskAction.Timer ->
            if (action.label.isBlank()) {
                resources.getString(R.string.task_do_timer, duration(action.seconds))
            } else {
                resources.getString(R.string.task_do_timer_labeled, duration(action.seconds), action.label)
            }
        is TaskAction.CalendarEvent -> resources.getString(R.string.task_do_calendar, action.title, action.durationMinutes)
        is TaskAction.SmsCompose -> resources.getString(R.string.task_do_sms, action.number, action.body)
        is TaskAction.Dial -> resources.getString(R.string.task_do_dial, action.number)
        is TaskAction.Speak -> resources.getString(R.string.task_do_speak, action.text)
        is TaskAction.Flashlight -> resources.getString(if (action.on) R.string.task_do_flashlight_on else R.string.task_do_flashlight_off)
        is TaskAction.Notify -> resources.getString(R.string.task_do_notify, action.title, action.text)
        is TaskAction.HttpRequest -> resources.getString(R.string.task_do_http, action.method.name, action.url)
        is TaskAction.MediaKey -> resources.getString(R.string.task_do_media, resources.getString(mediaRes(action.key)))
    }

    /** What happened to an action once the task ran. */
    fun outcome(resources: Resources, outcome: ActionOutcome): String = when (outcome) {
        is ActionOutcome.Done ->
            if (outcome.detail == null) resources.getString(R.string.task_result_done) else resources.getString(R.string.task_result_done_detail, outcome.detail)
        is ActionOutcome.OpenedFallback -> resources.getString(openedRes(outcome.what))
        is ActionOutcome.PermissionNeeded -> {
            val grant = resources.getString(grantRes(outcome.grant))
            if (outcome.fallbackApplied == FallbackReason.VIBRATE_INSTEAD_OF_SILENT) {
                resources.getString(R.string.task_result_vibrate_instead, grant)
            } else {
                resources.getString(R.string.task_result_permission_needed, grant)
            }
        }
        is ActionOutcome.Skipped -> resources.getString(
            when (outcome.why) {
                SkipReason.BLOCKED -> R.string.task_result_skipped_blocked
                SkipReason.NOT_FOREGROUND -> R.string.task_result_skipped_foreground
            },
        )
        is ActionOutcome.Failed ->
            if (outcome.why == FailReason.HTTP_STATUS) {
                resources.getString(R.string.task_fail_http_status, outcome.detail.orEmpty())
            } else {
                resources.getString(failRes(outcome.why))
            }
    }

    /** What to tell the user BEFORE running, or null when the action simply does what it says. */
    fun note(resources: Resources, capability: Capability): String? = when (capability) {
        Capability.Ready -> null
        is Capability.NeedsGrant -> {
            val grant = resources.getString(grantRes(capability.grant))
            if (capability.fallback == FallbackReason.VIBRATE_INSTEAD_OF_SILENT) {
                resources.getString(R.string.task_note_needs_grant_vibrate, grant)
            } else {
                resources.getString(R.string.task_note_needs_grant, grant)
            }
        }
        is Capability.FallbackOnly -> resources.getString(noteRes(capability.reason))
        is Capability.Unavailable -> resources.getString(R.string.task_note_unavailable)
    }

    @StringRes
    fun blocked(reason: BlockReason): Int = when (reason) {
        BlockReason.DISALLOWED_SCHEME -> R.string.task_blocked_scheme
        BlockReason.INVALID_URL -> R.string.task_blocked_url
        BlockReason.INVALID_NUMBER -> R.string.task_blocked_number
        BlockReason.INVALID_PACKAGE -> R.string.task_blocked_package
        BlockReason.EMPTY_VALUE -> R.string.task_blocked_empty
        BlockReason.OUT_OF_RANGE -> R.string.task_blocked_range
        BlockReason.TOO_LONG -> R.string.task_blocked_long
    }

    @StringRes
    fun risk(risk: TaskRisk): Int? = when (risk) {
        TaskRisk.NONE -> null
        TaskRisk.HTTP -> R.string.task_badge_http
        TaskRisk.SMS -> R.string.task_badge_sms
        TaskRisk.CALL -> R.string.task_badge_call
        TaskRisk.LINK -> R.string.task_badge_link
        TaskRisk.APP -> R.string.task_badge_app
        TaskRisk.WIFI -> R.string.task_badge_wifi
    }

    /** Title and explanation for a tag that could not become a task. */
    @StringRes
    fun errorTitle(error: TaskError): Int = when (error) {
        TaskError.NotATask -> R.string.task_error_not_task_title
        is TaskError.UnsupportedVersion -> R.string.task_error_version_title
        TaskError.TooLarge -> R.string.task_error_large_title
        is TaskError.UnknownOpcode, TaskError.Truncated, is TaskError.Malformed -> R.string.task_error_broken_title
    }

    fun errorBody(resources: Resources, error: TaskError): String = when (error) {
        TaskError.NotATask -> resources.getString(R.string.task_error_not_task_body)
        is TaskError.UnsupportedVersion -> resources.getString(R.string.task_error_version_body, error.version)
        TaskError.TooLarge -> resources.getString(R.string.task_error_large_body)
        is TaskError.UnknownOpcode -> resources.getString(R.string.task_error_opcode_body, "%02X".format(error.opcode))
        TaskError.Truncated, is TaskError.Malformed -> resources.getString(R.string.task_error_broken_body)
    }

    @StringRes
    fun grantRes(grant: SpecialGrant): Int = PermissionKind.of(grant).titleRes()

    // ---- small enums -------------------------------------------------------------------------------------------

    @StringRes
    fun ringerRes(mode: RingerMode): Int = when (mode) {
        RingerMode.SILENT -> R.string.task_ringer_silent
        RingerMode.VIBRATE -> R.string.task_ringer_vibrate
        RingerMode.NORMAL -> R.string.task_ringer_normal
    }

    @StringRes
    fun streamRes(stream: VolumeStream): Int = when (stream) {
        VolumeStream.MEDIA -> R.string.task_stream_media
        VolumeStream.RING -> R.string.task_stream_ring
        VolumeStream.ALARM -> R.string.task_stream_alarm
        VolumeStream.NOTIFICATION -> R.string.task_stream_notification
    }

    @StringRes
    fun joinAuthRes(auth: JoinAuth): Int = when (auth) {
        JoinAuth.OPEN -> R.string.task_join_open
        JoinAuth.WPA2 -> R.string.task_join_wpa2
        JoinAuth.WPA3 -> R.string.task_join_wpa3
    }

    @StringRes
    fun settingsRes(target: SettingsTarget): Int = when (target) {
        SettingsTarget.WIFI -> R.string.task_settings_wifi
        SettingsTarget.BLUETOOTH -> R.string.task_settings_bluetooth
        SettingsTarget.NFC -> R.string.task_settings_nfc
        SettingsTarget.LOCATION -> R.string.task_settings_location
        SettingsTarget.AIRPLANE -> R.string.task_settings_airplane
        SettingsTarget.MOBILE_DATA -> R.string.task_settings_mobile_data
        SettingsTarget.HOTSPOT -> R.string.task_settings_hotspot
        SettingsTarget.INTERNET_PANEL -> R.string.task_settings_internet
        SettingsTarget.VOLUME -> R.string.task_settings_volume
        SettingsTarget.DND -> R.string.task_settings_dnd
    }

    @StringRes
    fun mediaRes(key: MediaButton): Int = when (key) {
        MediaButton.PLAY_PAUSE -> R.string.task_media_play_pause
        MediaButton.NEXT -> R.string.task_media_next
        MediaButton.PREVIOUS -> R.string.task_media_previous
    }

    @StringRes
    private fun noteRes(reason: FallbackReason): Int = when (reason) {
        FallbackReason.CONNECTIVITY_PANEL -> R.string.task_note_connectivity_panel
        FallbackReason.WIFI_ADD_NETWORK_SCREEN -> R.string.task_note_wifi_add
        FallbackReason.WIFI_SUGGESTION_APPROVAL -> R.string.task_note_wifi_suggestion
        FallbackReason.BLUETOOTH_SETTINGS -> R.string.task_note_bluetooth
        FallbackReason.VIBRATE_INSTEAD_OF_SILENT -> R.string.task_note_needs_grant_vibrate_short
    }

    @StringRes
    private fun openedRes(reason: FallbackReason): Int = when (reason) {
        FallbackReason.CONNECTIVITY_PANEL -> R.string.task_result_connectivity_panel
        FallbackReason.WIFI_ADD_NETWORK_SCREEN -> R.string.task_result_wifi_add
        FallbackReason.WIFI_SUGGESTION_APPROVAL -> R.string.task_result_wifi_suggestion
        FallbackReason.BLUETOOTH_SETTINGS -> R.string.task_result_bluetooth
        FallbackReason.VIBRATE_INSTEAD_OF_SILENT -> R.string.task_result_vibrate_only
    }

    @StringRes
    private fun failRes(reason: FailReason): Int = when (reason) {
        FailReason.NOT_INSTALLED -> R.string.task_fail_not_installed
        FailReason.NO_HANDLER -> R.string.task_fail_no_handler
        FailReason.NETWORK -> R.string.task_fail_network
        FailReason.TIMEOUT -> R.string.task_fail_timeout
        FailReason.HTTP_STATUS -> R.string.task_fail_http_status
        FailReason.SECURITY -> R.string.task_fail_security
        FailReason.UNAVAILABLE -> R.string.task_fail_unavailable
        FailReason.UNEXPECTED -> R.string.task_fail_unexpected
    }

    private fun time(hour: Int, minute: Int) = "%02d:%02d".format(hour, minute)

    /** `1 h 5 min 30 s`: the unit abbreviations are the same in English and Spanish. */
    fun duration(totalSeconds: Int): String {
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return buildList {
            if (hours > 0) add("$hours h")
            if (minutes > 0) add("$minutes min")
            if (seconds > 0 || isEmpty()) add("$seconds s")
        }.joinToString(" ")
    }
}
