package com.tagforge.app.feature.taskrunner

/** What an incoming intent asks the task runner to put on the confirmation sheet. */
sealed interface EntryRequest {
    data class SavedTask(val id: Long) : EntryRequest

    /** [bytes] is a raw NDEF message, or null when the intent carried none (the sheet then shows an error). */
    data class Ndef(val bytes: ByteArray?) : EntryRequest {
        override fun equals(other: Any?): Boolean = other is Ndef && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
    }
}

/**
 * The trust rule of the task runner (W5). The exported entry is reached by the NFC system AND by any app, so it only
 * honours a tag the system dispatched ([ACTION_NDEF_DISCOVERED]); the internal extras (a saved task id, raw NDEF bytes
 * handed over from the Read tab) are honoured only on the non-exported entry that nothing outside this app can start.
 */
object TaskRunnerEntry {
    const val NO_TASK = -1L
    const val ACTION_NDEF_DISCOVERED = "android.nfc.action.NDEF_DISCOVERED"

    fun resolve(
        trusted: Boolean,
        action: String?,
        taskId: Long,
        extraBytes: ByteArray?,
        systemTagBytes: ByteArray?,
    ): EntryRequest {
        if (trusted) {
            if (taskId != NO_TASK) return EntryRequest.SavedTask(taskId)
            return EntryRequest.Ndef(extraBytes ?: systemTagBytes)
        }
        return EntryRequest.Ndef(if (action == ACTION_NDEF_DISCOVERED) systemTagBytes else null)
    }
}
