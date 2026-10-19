package com.tagforge.domain.history

import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ports.HistoryRepository
import com.tagforge.domain.ports.SettingsStore
import com.tagforge.domain.read.TagScan
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagInfo
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/**
 * Appends to the history unless the user turned "save history" off. Passwords never get through: the summary shows
 * only the SSID and the stored bytes are redacted.
 */
class RecordHistoryUseCase(
    private val repository: HistoryRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
) {

    suspend fun recordRead(scan: TagScan) = record(HistoryKind.READ, scan.readout.info, scan.readout.message, error = null)

    /** [info] is null when no tag was ever reached; [error] is null on success. */
    suspend fun recordWrite(info: TagInfo?, message: NdefMessage?, error: TagError?) =
        record(HistoryKind.WRITE, info, message, error)

    /** An NS operation (password, lock, configuration). [summary] comes from SecuritySummary and never holds a secret. */
    suspend fun recordSecurity(info: TagInfo?, summary: String, error: TagError?) {
        if (!settings.settings.first().saveHistory) return
        repository.add(
            HistoryEntry(
                at = clock.now(),
                kind = HistoryKind.SECURITY,
                outcome = HistoryOutcome.of(error),
                uid = info?.uid,
                tagType = info?.let { it.chipName ?: it.family.code },
                summary = summary,
                ndef = null,
            ),
        )
    }

    suspend fun record(kind: HistoryKind, info: TagInfo?, message: NdefMessage?, error: TagError?) {
        if (!settings.settings.first().saveHistory) return
        val safe = message?.takeUnless { it.isEmpty }?.let(HistorySummarizer::redact)
        repository.add(
            HistoryEntry(
                at = clock.now(),
                kind = kind,
                outcome = HistoryOutcome.of(error),
                uid = info?.uid,
                tagType = info?.let { it.chipName ?: it.family.code },
                summary = HistorySummarizer.summarize(safe),
                ndef = safe?.let(DefaultNdefCodec::encode),
            ),
        )
    }
}
