package com.tagforge.domain.write

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.tag.TagError

/**
 * The "this tag already holds data" question shared by every operation that replaces content (write, clone).
 * Declining is `Cancelled` with zero writes (G5).
 */
internal object OverwriteGuard {

    /**
     * Null = go ahead. Otherwise the error to return: the user declined, or the tag could not be inspected.
     * [confirmOverwrite] receives the existing message, or null when the tag holds data that cannot be parsed.
     */
    suspend fun check(session: TagSession, confirmOverwrite: suspend (existing: NdefMessage?) -> Boolean): Outcome.Err<TagError>? =
        when (val read = session.readNdef()) {
            is Outcome.Ok -> {
                val existing = read.value
                if (existing == null || confirmOverwrite(existing)) null else Outcome.Err(TagError.Cancelled)
            }
            is Outcome.Err -> when (read.error) {
                TagError.NotNdef -> null   // blank tag: nothing to lose
                is TagError.Malformed -> if (confirmOverwrite(null)) null else Outcome.Err(TagError.Cancelled)
                is TagError.Nak, TagError.AuthRequired -> Outcome.Err(TagError.AuthRequired)
                else -> read
            }
        }
}
