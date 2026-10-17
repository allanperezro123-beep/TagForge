package com.tagforge.app.infrastructure.tasks

import com.tagforge.domain.task.ActionOutcome
import com.tagforge.domain.task.Capability
import com.tagforge.domain.task.DeviceFacts
import com.tagforge.domain.task.FallbackReason
import com.tagforge.domain.task.HttpMethod
import com.tagforge.domain.task.JoinAuth
import com.tagforge.domain.task.MediaButton
import com.tagforge.domain.task.RingerMode
import com.tagforge.domain.task.SettingsTarget
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.task.VolumeStream
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TK-07 coverage: every one of the 19 action types reaches ITS handler and nothing else. Exhaustiveness is also a
 * compile-time fact (the executor dispatches with a `when` over the sealed [TaskAction]), so adding an action type
 * without a handler does not build.
 */
class ExecutorCoverageTest {

    private val calls = mutableListOf<String>()

    private fun <A : TaskAction> recording(name: String) = ActionHandler<A> {
        calls += name
        ActionOutcome.Done(name)
    }

    private val handlers = TaskHandlers(
        openUrl = recording("openUrl"),
        launchApp = recording("launchApp"),
        setRinger = recording("setRinger"),
        setVolume = recording("setVolume"),
        setBrightness = recording("setBrightness"),
        wifiToggle = recording("wifiToggle"),
        wifiJoin = recording("wifiJoin"),
        bluetoothToggle = recording("bluetoothToggle"),
        openSettings = recording("openSettings"),
        alarm = recording("alarm"),
        timer = recording("timer"),
        calendarEvent = recording("calendarEvent"),
        smsCompose = recording("smsCompose"),
        dial = recording("dial"),
        speak = recording("speak"),
        flashlight = recording("flashlight"),
        notify = recording("notify"),
        httpRequest = recording("httpRequest"),
        mediaKey = recording("mediaKey"),
    )

    private val samples: List<Pair<TaskAction, String>> = listOf(
        TaskAction.OpenUrl("https://example.com") to "openUrl",
        TaskAction.LaunchApp("com.example.app") to "launchApp",
        TaskAction.SetRinger(RingerMode.VIBRATE) to "setRinger",
        TaskAction.SetVolume(VolumeStream.MEDIA, 50) to "setVolume",
        TaskAction.SetBrightness(100) to "setBrightness",
        TaskAction.WifiToggle(true) to "wifiToggle",
        TaskAction.WifiJoin("Home", "pass1234", JoinAuth.WPA2) to "wifiJoin",
        TaskAction.BluetoothToggle(true) to "bluetoothToggle",
        TaskAction.OpenSettings(SettingsTarget.NFC) to "openSettings",
        TaskAction.Alarm(7, 30) to "alarm",
        TaskAction.Timer(60) to "timer",
        TaskAction.CalendarEvent("Standup", 15) to "calendarEvent",
        TaskAction.SmsCompose("123", "hi") to "smsCompose",
        TaskAction.Dial("123") to "dial",
        TaskAction.Speak("hello") to "speak",
        TaskAction.Flashlight(true) to "flashlight",
        TaskAction.Notify("t", "b") to "notify",
        TaskAction.HttpRequest("https://example.com/h", HttpMethod.GET) to "httpRequest",
        TaskAction.MediaKey(MediaButton.PLAY_PAUSE) to "mediaKey",
    )

    private val executor = AndroidTaskActionExecutor(handlers) { DeviceFacts(34, writeSettingsGranted = false, notificationPolicyGranted = false, postNotificationsGranted = false) }

    @Test
    fun theSampleListCoversAllNineteenActionTypes() {
        assertEquals(19, samples.map { it.first::class }.toSet().size)
        assertEquals(19, samples.map { it.second }.toSet().size, "one distinct handler each")
    }

    @Test
    fun eachActionTypeIsRoutedToItsOwnHandlerAndNoOther() = runTest {
        samples.forEach { (action, expectedHandler) ->
            calls.clear()

            val outcome = executor.execute(action)

            assertEquals(listOf(expectedHandler), calls, "wrong routing for $action")
            assertEquals(ActionOutcome.Done(expectedHandler), outcome)
        }
    }

    @Test
    fun capabilityIsTheFeasibilityMatrixOverTheCurrentDeviceFacts() {
        assertEquals(Capability.FallbackOnly(FallbackReason.CONNECTIVITY_PANEL), executor.capability(TaskAction.WifiToggle(true)))
        assertEquals(Capability.Ready, executor.capability(TaskAction.Dial("123")))

        val legacy = AndroidTaskActionExecutor(handlers) { DeviceFacts(27, true, true, true) }
        assertEquals(Capability.Ready, legacy.capability(TaskAction.WifiToggle(true)))
        assertEquals(Capability.Ready, legacy.capability(TaskAction.SetBrightness(5)))
    }

    @Test
    fun askingAboutCapabilityNeverRunsAHandler() {
        samples.forEach { executor.capability(it.first) }

        assertEquals(emptyList(), calls)
    }
}
