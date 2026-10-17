package com.tagforge.domain.task

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TK-01: an ordered list of 1..16 typed actions, editable, with elevated-risk badges and a JSON form for Room/export. */
class TaskModelTest {

    private val ringer = TaskAction.SetRinger(RingerMode.VIBRATE)
    private val url = TaskAction.OpenUrl("https://example.com")

    @Test
    fun addingAndReorderingKeepsTheNewOrder() {
        val draft = TaskDraft().add(ringer).add(url)
        assertEquals(listOf(ringer, url), draft.actions)

        val moved = draft.move(1, -1)

        assertEquals(listOf(url, ringer), moved.actions)
    }

    @Test
    fun movingPastEitherEndChangesNothing() {
        val draft = TaskDraft().add(ringer).add(url)

        assertEquals(draft, draft.move(0, -1))
        assertEquals(draft, draft.move(1, 1))
        assertEquals(draft, draft.move(7, 1))
    }

    @Test
    fun theSeventeenthActionIsRejected() {
        var draft = TaskDraft()
        repeat(TaskLimits.MAX_ACTIONS) { draft = draft.add(TaskAction.Flashlight(it % 2 == 0)) }

        assertEquals(16, draft.actions.size)
        assertTrue(draft.isFull)
        assertEquals(draft, draft.add(ringer), "a full task must not grow")
        assertEquals(16, draft.add(ringer).actions.size)
    }

    @Test
    fun removeAndReplaceTouchOnlyTheGivenIndex() {
        val draft = TaskDraft().add(ringer).add(url).add(TaskAction.Dial("123"))

        assertEquals(listOf(ringer, TaskAction.Dial("123")), draft.remove(1).actions)
        assertEquals(listOf(ringer, TaskAction.Speak("hi"), TaskAction.Dial("123")), draft.replace(1, TaskAction.Speak("hi")).actions)
        assertEquals(draft, draft.remove(9))
        assertEquals(draft, draft.replace(-1, ringer))
        assertFalse(TaskDraft().isFull)
    }

    @Test
    fun everyActionTypeSurvivesAJsonRoundTrip() {
        val all = listOf(
            TaskAction.SetRinger(RingerMode.SILENT),
            TaskAction.SetVolume(VolumeStream.ALARM, 40),
            TaskAction.SetBrightness(200),
            TaskAction.WifiToggle(true),
            TaskAction.WifiJoin("Home", "pass1234", JoinAuth.WPA3),
            TaskAction.BluetoothToggle(false),
            TaskAction.OpenSettings(SettingsTarget.AIRPLANE),
            url,
            TaskAction.LaunchApp("com.example.app"),
            TaskAction.Alarm(7, 30, "Wake up"),
            TaskAction.Timer(90, "Tea"),
            TaskAction.CalendarEvent("Standup", 15),
            TaskAction.SmsCompose("+573001234567", "hi"),
            TaskAction.Dial("123"),
            TaskAction.Speak("hello ñandú"),
            TaskAction.Flashlight(true),
            TaskAction.Notify("Title", "Body"),
            TaskAction.HttpRequest("https://example.com/hook", HttpMethod.POST),
            TaskAction.MediaKey(MediaButton.NEXT),
        )

        val decoded = TaskJson.decode(TaskJson.encode(all))

        assertEquals(all, decoded)
        assertEquals(19, all.size, "one sample per spec action type")
    }

    @Test
    fun jsonUsesStableNamesAndSurvivesUnknownKeys() {
        val text = TaskJson.encode(listOf(TaskAction.SetRinger(RingerMode.VIBRATE)))
        assertTrue("\"t\":\"ringer\"" in text, text)
        assertTrue("\"vibrate\"" in text, text)

        val withExtra = """[{"t":"ringer","mode":"normal","future":1}]"""
        assertEquals(listOf(TaskAction.SetRinger(RingerMode.NORMAL)), TaskJson.decode(withExtra))
    }

    @Test
    fun unreadableJsonDecodesToNullNotACrash() {
        assertNull(TaskJson.decode("not json"))
        assertNull(TaskJson.decode("""[{"t":"teleport"}]"""), "an action type this build does not know")
    }

    @Test
    fun onlyTheSixElevatedActionsCarryARiskBadge() {
        assertEquals(TaskRisk.HTTP, TaskAction.HttpRequest("https://a.b").risk)
        assertEquals(TaskRisk.SMS, TaskAction.SmsCompose("1").risk)
        assertEquals(TaskRisk.CALL, TaskAction.Dial("1").risk)
        assertEquals(TaskRisk.LINK, url.risk)
        assertEquals(TaskRisk.APP, TaskAction.LaunchApp("a.b").risk)
        assertEquals(TaskRisk.WIFI, TaskAction.WifiJoin("x").risk)
        assertTrue(TaskRisk.WIFI.isElevated)

        assertEquals(TaskRisk.NONE, ringer.risk)
        assertEquals(TaskRisk.NONE, TaskAction.Speak("x").risk)
        assertFalse(TaskRisk.NONE.isElevated)
    }

    @Test
    fun aTaskKnowsItsPayloadForTheTagAndItsNameFallback() {
        val task = Task(id = 3, name = "Night", actions = listOf(ringer), createdAt = kotlin.time.Instant.fromEpochMilliseconds(1), updatedAt = kotlin.time.Instant.fromEpochMilliseconds(2))

        assertEquals(TaskPayload("Night", listOf(ringer)), task.payload)
        assertEquals(TaskPayload(null, listOf(ringer)), task.copy(name = " ").payload)
    }
}
