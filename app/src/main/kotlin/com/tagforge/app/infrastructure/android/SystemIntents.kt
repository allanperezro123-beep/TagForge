package com.tagforge.app.infrastructure.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import com.tagforge.domain.task.SettingsTarget

/**
 * Every system Intent the app fires, in one place. Builders are plain functions of their inputs; [IntentLauncher]
 * starts them and turns "nothing can handle this" into a result instead of a crash.
 */
object SystemIntents {

    fun view(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)

    /** Opens the dialer prefilled. Nothing is dialled: the user presses call. */
    fun dial(number: String): Intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))

    /** Opens the messaging app prefilled. Nothing is sent by Tag Forge (no SEND_SMS permission exists). */
    fun smsCompose(number: String, body: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).putExtra("sms_body", body)

    fun alarm(hour: Int, minute: Int, label: String): Intent = Intent(AlarmClock.ACTION_SET_ALARM)
        .putExtra(AlarmClock.EXTRA_HOUR, hour)
        .putExtra(AlarmClock.EXTRA_MINUTES, minute)
        .putExtra(AlarmClock.EXTRA_MESSAGE, label)
        .putExtra(AlarmClock.EXTRA_SKIP_UI, false)

    fun timer(seconds: Int, label: String): Intent = Intent(AlarmClock.ACTION_SET_TIMER)
        .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
        .putExtra(AlarmClock.EXTRA_MESSAGE, label)
        .putExtra(AlarmClock.EXTRA_SKIP_UI, false)

    fun calendarEvent(title: String, startMillis: Long, durationMinutes: Int): Intent =
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + durationMinutes * MILLIS_PER_MINUTE)

    /**
     * The screen for [target]. Toggles an app cannot flip (mobile data, airplane, hotspot, GPS, NFC) end up here.
     * Settings panels exist from Android 10; older versions get the closest full screen.
     */
    fun settings(target: SettingsTarget, apiLevel: Int = Build.VERSION.SDK_INT): Intent {
        val panels = apiLevel >= Build.VERSION_CODES.Q
        val action = when (target) {
            SettingsTarget.WIFI -> Settings.ACTION_WIFI_SETTINGS
            SettingsTarget.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            SettingsTarget.NFC -> Settings.ACTION_NFC_SETTINGS
            SettingsTarget.LOCATION -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            SettingsTarget.AIRPLANE -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            SettingsTarget.MOBILE_DATA, SettingsTarget.INTERNET_PANEL ->
                if (panels) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS
            SettingsTarget.HOTSPOT -> Settings.ACTION_WIRELESS_SETTINGS
            SettingsTarget.VOLUME -> if (panels) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS
            SettingsTarget.DND -> Settings.ACTION_SOUND_SETTINGS
        }
        return Intent(action)
    }

    /** The internet connectivity panel (Android 10+) that stands in for the Wi-Fi switch an app can no longer flip. */
    fun connectivityPanel(): Intent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)

    fun bluetoothSettings(): Intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)

    fun wifiSettings(): Intent = Intent(Settings.ACTION_WIFI_SETTINGS)

    // ---- grants (Permission Center) -------------------------------------------------------------------------

    fun manageWriteSettings(packageName: String): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))

    fun notificationPolicyAccess(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun nfcSettings(): Intent = Intent(Settings.ACTION_NFC_SETTINGS)

    fun appNotificationSettings(packageName: String): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

    private const val MILLIS_PER_MINUTE = 60_000L
}

/** What happened when an Intent was started. */
enum class LaunchResult { STARTED, NO_HANDLER, DENIED }

/** Starts Intents from the application context. [NEW_TASK] is required there and harmless from the runner. */
class IntentLauncher(private val context: Context) {

    fun launch(intent: Intent): LaunchResult = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        LaunchResult.STARTED
    } catch (_: ActivityNotFoundException) {
        LaunchResult.NO_HANDLER
    } catch (_: SecurityException) {
        LaunchResult.DENIED
    }

    /** Tries each Intent in turn until one starts: the specific panel first, a full settings screen after. */
    fun launchFirst(vararg intents: Intent): LaunchResult {
        var last = LaunchResult.NO_HANDLER
        for (intent in intents) {
            last = launch(intent)
            if (last == LaunchResult.STARTED) return last
        }
        return last
    }
}
