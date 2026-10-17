package com.tagforge.domain.task

import kotlin.test.Test
import kotlin.test.assertEquals

/** TK-07: what each action can really do on each Android version, and what it does instead. Never a fake success. */
class FeasibilityMatrixTest {

    private fun facts(
        api: Int,
        writeSettings: Boolean = false,
        policy: Boolean = false,
        notifications: Boolean = false,
        flashlight: Boolean = true,
    ) = DeviceFacts(api, writeSettings, policy, notifications, flashlight)

    private fun capability(action: TaskAction, facts: DeviceFacts) = FeasibilityMatrix.capability(action, facts)

    // ---- connectivity -------------------------------------------------------------------------------------

    @Test
    fun wifiToggleIsDirectOnlyBeforeAndroid10() {
        listOf(26, 27, 28).forEach { assertEquals(Capability.Ready, capability(TaskAction.WifiToggle(true), facts(it)), "API $it") }
        listOf(29, 30, 33, 34, 36).forEach {
            assertEquals(Capability.FallbackOnly(FallbackReason.CONNECTIVITY_PANEL), capability(TaskAction.WifiToggle(false), facts(it)), "API $it")
        }
    }

    @Test
    fun wifiJoinUsesTheBestMechanismOfEachEra() {
        listOf(26, 28).forEach { assertEquals(Capability.Ready, capability(TaskAction.WifiJoin("Home", "pass1234"), facts(it)), "legacy config, API $it") }
        assertEquals(Capability.FallbackOnly(FallbackReason.WIFI_SUGGESTION_APPROVAL), capability(TaskAction.WifiJoin("Home", "pass1234"), facts(29)))
        listOf(30, 34, 36).forEach {
            assertEquals(Capability.FallbackOnly(FallbackReason.WIFI_ADD_NETWORK_SCREEN), capability(TaskAction.WifiJoin("Home", "pass1234"), facts(it)), "API $it")
        }
    }

    @Test
    fun bluetoothIsNeverToggledSilently() {
        listOf(26, 30, 33, 34, 36).forEach {
            assertEquals(Capability.FallbackOnly(FallbackReason.BLUETOOTH_SETTINGS), capability(TaskAction.BluetoothToggle(true), facts(it)), "API $it")
            assertEquals(Capability.FallbackOnly(FallbackReason.BLUETOOTH_SETTINGS), capability(TaskAction.BluetoothToggle(false), facts(it)), "API $it")
        }
    }

    // ---- special grants -----------------------------------------------------------------------------------

    @Test
    fun brightnessNeedsModifySystemSettings() {
        assertEquals(Capability.NeedsGrant(SpecialGrant.WRITE_SETTINGS), capability(TaskAction.SetBrightness(100), facts(34)))
        assertEquals(Capability.Ready, capability(TaskAction.SetBrightness(100), facts(34, writeSettings = true)))
    }

    @Test
    fun silentNeedsDoNotDisturbAccessAndNamesItsFallback() {
        assertEquals(
            Capability.NeedsGrant(SpecialGrant.NOTIFICATION_POLICY, FallbackReason.VIBRATE_INSTEAD_OF_SILENT),
            capability(TaskAction.SetRinger(RingerMode.SILENT), facts(34)),
        )
        assertEquals(Capability.Ready, capability(TaskAction.SetRinger(RingerMode.SILENT), facts(34, policy = true)))
        assertEquals(Capability.Ready, capability(TaskAction.SetRinger(RingerMode.VIBRATE), facts(34)))
        assertEquals(Capability.Ready, capability(TaskAction.SetRinger(RingerMode.NORMAL), facts(34)))
    }

    @Test
    fun muffingTheRingerVolumeToZeroAlsoNeedsDoNotDisturbAccess() {
        val needs = Capability.NeedsGrant(SpecialGrant.NOTIFICATION_POLICY)
        assertEquals(needs, capability(TaskAction.SetVolume(VolumeStream.RING, 0), facts(34)))
        assertEquals(needs, capability(TaskAction.SetVolume(VolumeStream.NOTIFICATION, 0), facts(34)))
        assertEquals(Capability.Ready, capability(TaskAction.SetVolume(VolumeStream.RING, 0), facts(34, policy = true)))
        assertEquals(Capability.Ready, capability(TaskAction.SetVolume(VolumeStream.RING, 30), facts(34)))
        assertEquals(Capability.Ready, capability(TaskAction.SetVolume(VolumeStream.MEDIA, 0), facts(34)))
    }

    @Test
    fun notificationsNeedTheRuntimePermissionFromAndroid13() {
        val needs = Capability.NeedsGrant(SpecialGrant.POST_NOTIFICATIONS)
        assertEquals(needs, capability(TaskAction.Notify("t", "b"), facts(33)))
        assertEquals(needs, capability(TaskAction.Notify("t", "b"), facts(36)))
        assertEquals(Capability.Ready, capability(TaskAction.Notify("t", "b"), facts(33, notifications = true)))
        assertEquals(Capability.Ready, capability(TaskAction.Notify("t", "b"), facts(32)))
        assertEquals(Capability.Ready, capability(TaskAction.Notify("t", "b"), facts(26)))
    }

    // ---- compose-only and direct -----------------------------------------------------------------------------

    @Test
    fun smsAndDialOnlyOpenTheirAppsAndAreReadyWithoutAnyPermission() {
        listOf(26, 34).forEach {
            assertEquals(Capability.Ready, capability(TaskAction.SmsCompose("123", "hi"), facts(it)))
            assertEquals(Capability.Ready, capability(TaskAction.Dial("123"), facts(it)))
        }
    }

    @Test
    fun theRemainingActionsAreReadyOnEveryVersion() {
        val readyOnAll = listOf(
            TaskAction.OpenUrl("https://example.com"), TaskAction.LaunchApp("com.x.y"), TaskAction.Alarm(7, 0), TaskAction.Timer(60),
            TaskAction.CalendarEvent("x", 30), TaskAction.Speak("hi"), TaskAction.HttpRequest("https://a.b"),
            TaskAction.MediaKey(MediaButton.NEXT), TaskAction.OpenSettings(SettingsTarget.AIRPLANE), TaskAction.Flashlight(true),
        )
        (26..36).forEach { api -> readyOnAll.forEach { assertEquals(Capability.Ready, capability(it, facts(api)), "$it on API $api") } }
    }

    @Test
    fun flashlightIsUnavailableOnADeviceWithoutOne() {
        assertEquals(Capability.Unavailable(UnavailableReason.NO_FLASHLIGHT), capability(TaskAction.Flashlight(true), facts(34, flashlight = false)))
        assertEquals(Capability.Ready, capability(TaskAction.Flashlight(true), facts(34, flashlight = true)))
    }
}
