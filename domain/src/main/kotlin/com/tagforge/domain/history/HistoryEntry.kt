package com.tagforge.domain.history

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.tag.TagError
import kotlin.time.Instant

/** What the user did. Persisted by stable [code]; never rename a code. */
enum class HistoryKind(val code: String) {
    READ("read"),
    WRITE("write"),
    ERASE("erase"),
    FORMAT("format"),
    CLONE("clone"),
    BATCH("batch"),
    ;

    companion object {
        fun fromCode(code: String?): HistoryKind = entries.firstOrNull { it.code == code } ?: READ
    }
}

/** How it ended. Persisted by stable [code]; never rename a code. */
enum class HistoryOutcome(val code: String) {
    SUCCESS("success"),
    FAILED_TAGLOST("failed_taglost"),
    FAILED_READ_ONLY("failed_read_only"),
    FAILED_CAPACITY("failed_capacity"),
    FAILED_VERIFY("failed_verify"),
    FAILED_AUTH("failed_auth"),
    FAILED_OTHER("failed_other"),
    ;

    val isSuccess: Boolean get() = this == SUCCESS

    companion object {
        fun fromCode(code: String?): HistoryOutcome = entries.firstOrNull { it.code == code } ?: FAILED_OTHER

        /** `null` means the operation succeeded. */
        fun of(error: TagError?): HistoryOutcome = when (error) {
            null -> SUCCESS
            TagError.TagLost -> FAILED_TAGLOST
            TagError.ReadOnly -> FAILED_READ_ONLY
            is TagError.CapacityExceeded -> FAILED_CAPACITY
            is TagError.VerifyMismatch -> FAILED_VERIFY
            TagError.AuthRequired, is TagError.AuthFailed -> FAILED_AUTH
            else -> FAILED_OTHER
        }
    }
}

/**
 * One line of history. [ndef] is the raw NDEF message with credentials removed (see [HistorySummarizer.redact]);
 * passwords are never stored.
 * @property id 0 until the repository assigns one
 * @property tagType `NTAG213` etc. when known, else the tag family code
 */
data class HistoryEntry(
    val id: Long = 0,
    val at: Instant,
    val kind: HistoryKind,
    val outcome: HistoryOutcome,
    val uid: Bytes?,
    val tagType: String?,
    val summary: String,
    val ndef: Bytes?,
)

/** What to list. Search and filters arrive with Phase 5. */
data class HistoryQuery(val limit: Int = DEFAULT_LIMIT) {
    companion object {
        const val DEFAULT_LIMIT = 500
    }
}
