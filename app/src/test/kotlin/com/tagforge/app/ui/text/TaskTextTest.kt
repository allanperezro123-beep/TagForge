package com.tagforge.app.ui.text

import com.tagforge.app.feature.settings.PermissionKind
import com.tagforge.domain.task.HttpMethod
import com.tagforge.domain.task.RingerMode
import com.tagforge.domain.task.SpecialGrant
import com.tagforge.domain.task.TaskAction
import kotlin.test.Test
import kotlin.test.assertEquals

/** The pure helpers behind the task wording (resource lookups are covered by the parity test and by lint). */
class TaskTextTest {

    @Test
    fun durationsReadAsHoursMinutesSeconds() {
        assertEquals("30 s", TaskText.duration(30))
        assertEquals("1 min 30 s", TaskText.duration(90))
        assertEquals("5 min", TaskText.duration(300))
        assertEquals("1 h", TaskText.duration(3600))
        assertEquals("1 h 1 min 1 s", TaskText.duration(3661))
        assertEquals("18 h 12 min 15 s", TaskText.duration(65535))
    }

    @Test
    fun aZeroDurationStillSaysSomething() {
        assertEquals("0 s", TaskText.duration(0))
    }

    @Test
    fun everyGrantMapsToTheRowThatFixesIt() {
        assertEquals(PermissionKind.WRITE_SETTINGS, PermissionKind.of(SpecialGrant.WRITE_SETTINGS))
        assertEquals(PermissionKind.NOTIFICATION_POLICY, PermissionKind.of(SpecialGrant.NOTIFICATION_POLICY))
        assertEquals(PermissionKind.NOTIFICATIONS, PermissionKind.of(SpecialGrant.POST_NOTIFICATIONS))
    }

    @Test
    fun webActionsNameTheHostTheyReallyContact() {
        assertEquals("evil.example.net", TaskText.hostOf(TaskAction.OpenUrl("https://Evil.Example.net/login")))
        assertEquals("192.168.1.5:8080", TaskText.hostOf(TaskAction.HttpRequest("http://192.168.1.5:8080/on", HttpMethod.POST)))
    }

    @Test
    fun otherActionsAndNonWebLinksHaveNoHostLine() {
        assertEquals(null, TaskText.hostOf(TaskAction.OpenUrl("tel:+573001234567")))
        assertEquals(null, TaskText.hostOf(TaskAction.SetRinger(RingerMode.VIBRATE)))
    }
}
