package com.tagforge.app.feature.taskrunner

import kotlin.test.Test
import kotlin.test.assertEquals

/** W5: any app can start an exported activity with extras; only our own non-exported entry may use the internal ones. */
class TaskRunnerEntryTest {

    private val discovered = TaskRunnerEntry.ACTION_NDEF_DISCOVERED
    private val none = TaskRunnerEntry.NO_TASK
    private val systemTag = byteArrayOf(1, 2, 3)
    private val handedOver = byteArrayOf(9, 9)

    @Test
    fun theExportedEntryIgnoresASavedTaskIdSentByAnotherApp() {
        val request = TaskRunnerEntry.resolve(trusted = false, action = "android.intent.action.VIEW", taskId = 7, extraBytes = null, systemTagBytes = null)

        assertEquals(EntryRequest.Ndef(null), request)
    }

    @Test
    fun theExportedEntryIgnoresRawBytesSentByAnotherAppAndOnlyTrustsTheSystemsTag() {
        assertEquals(
            EntryRequest.Ndef(systemTag),
            TaskRunnerEntry.resolve(trusted = false, action = discovered, taskId = none, extraBytes = handedOver, systemTagBytes = systemTag),
        )
        assertEquals(
            EntryRequest.Ndef(null),
            TaskRunnerEntry.resolve(trusted = false, action = "android.intent.action.MAIN", taskId = none, extraBytes = handedOver, systemTagBytes = systemTag),
            "tag messages are only believed on NDEF_DISCOVERED",
        )
        assertEquals(
            EntryRequest.Ndef(null),
            TaskRunnerEntry.resolve(trusted = false, action = null, taskId = none, extraBytes = handedOver, systemTagBytes = null),
        )
    }

    @Test
    fun theInternalEntryHonoursTheSavedTaskIdAndTheHandedOverBytes() {
        assertEquals(EntryRequest.SavedTask(7), TaskRunnerEntry.resolve(trusted = true, action = null, taskId = 7, extraBytes = handedOver, systemTagBytes = null))
        assertEquals(EntryRequest.Ndef(handedOver), TaskRunnerEntry.resolve(trusted = true, action = null, taskId = none, extraBytes = handedOver, systemTagBytes = null))
        assertEquals(EntryRequest.Ndef(null), TaskRunnerEntry.resolve(trusted = true, action = null, taskId = none, extraBytes = null, systemTagBytes = null))
    }
}
