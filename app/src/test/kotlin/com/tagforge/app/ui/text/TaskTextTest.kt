package com.tagforge.app.ui.text

import com.tagforge.app.feature.settings.PermissionKind
import com.tagforge.domain.task.SpecialGrant
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
}
