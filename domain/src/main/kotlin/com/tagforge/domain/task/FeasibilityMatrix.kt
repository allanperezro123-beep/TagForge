package com.tagforge.domain.task

/**
 * The TK-07 matrix as a pure function of (action, Android version, grants). The Android executor asks it before
 * running anything and the confirmation sheet shows the answer, so what the sheet promises is what the executor does.
 */
object FeasibilityMatrix {

    private const val ANDROID_10 = 29
    private const val ANDROID_11 = 30
    private const val ANDROID_13 = 33

    fun capability(action: TaskAction, facts: DeviceFacts): Capability = when (action) {
        is TaskAction.WifiToggle ->
            if (facts.apiLevel >= ANDROID_10) Capability.FallbackOnly(FallbackReason.CONNECTIVITY_PANEL) else Capability.Ready

        is TaskAction.WifiJoin -> when {
            facts.apiLevel >= ANDROID_11 -> Capability.FallbackOnly(FallbackReason.WIFI_ADD_NETWORK_SCREEN)
            facts.apiLevel == ANDROID_10 -> Capability.FallbackOnly(FallbackReason.WIFI_SUGGESTION_APPROVAL)
            else -> Capability.Ready
        }

        is TaskAction.BluetoothToggle -> Capability.FallbackOnly(FallbackReason.BLUETOOTH_SETTINGS)

        is TaskAction.SetBrightness ->
            if (facts.writeSettingsGranted) Capability.Ready else Capability.NeedsGrant(SpecialGrant.WRITE_SETTINGS)

        is TaskAction.SetRinger ->
            if (action.mode == RingerMode.SILENT && !facts.notificationPolicyGranted) {
                Capability.NeedsGrant(SpecialGrant.NOTIFICATION_POLICY, FallbackReason.VIBRATE_INSTEAD_OF_SILENT)
            } else {
                Capability.Ready
            }

        is TaskAction.SetVolume -> {
            val silencesRinger = action.level == 0 && (action.stream == VolumeStream.RING || action.stream == VolumeStream.NOTIFICATION)
            if (silencesRinger && !facts.notificationPolicyGranted) Capability.NeedsGrant(SpecialGrant.NOTIFICATION_POLICY) else Capability.Ready
        }

        is TaskAction.Notify ->
            if (facts.apiLevel >= ANDROID_13 && !facts.postNotificationsGranted) Capability.NeedsGrant(SpecialGrant.POST_NOTIFICATIONS) else Capability.Ready

        is TaskAction.Flashlight ->
            if (facts.hasFlashlight) Capability.Ready else Capability.Unavailable(UnavailableReason.NO_FLASHLIGHT)

        is TaskAction.OpenUrl,
        is TaskAction.LaunchApp,
        is TaskAction.OpenSettings,
        is TaskAction.Alarm,
        is TaskAction.Timer,
        is TaskAction.CalendarEvent,
        is TaskAction.SmsCompose,
        is TaskAction.Dial,
        is TaskAction.Speak,
        is TaskAction.HttpRequest,
        is TaskAction.MediaKey,
        -> Capability.Ready
    }
}
