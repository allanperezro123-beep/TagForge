package com.tagforge.domain.history

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.security.SecurityFailure
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
            is TagError.Security -> when (val failure = error.failure) {
                is SecurityFailure.Partial -> of(failure.cause)
                SecurityFailure.AuthExhausted -> FAILED_AUTH
                else -> FAILED_OTHER
            }
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

/** Outcome filter for the history list: every entry, only the successful ones, or only the failed ones. */
enum class OutcomeFilter { ALL, SUCCESS, FAILED }

/**
 * What to list (HT-03). Every criterion narrows the list (AND):
 * - [text]: case-insensitive, over the summary, the tag type and the UID (separators `:`, `-` and spaces are ignored
 *   for the UID); blank means no text filter
 * - [kinds]: empty means every kind
 * - [outcome], [from] and [to] (both inclusive)
 * [limit] applies after filtering.
 */
data class HistoryQuery(
    val limit: Int = DEFAULT_LIMIT,
    val text: String = "",
    val kinds: Set<HistoryKind> = emptySet(),
    val outcome: OutcomeFilter = OutcomeFilter.ALL,
    val from: Instant? = null,
    val to: Instant? = null,
) {
    /** [text] trimmed; empty when there is no text filter. */
    val textNeedle: String get() = text.trim()

    /** [text] reduced to what can appear in an uppercase hex UID (no separators); used against the stored UID. */
    val uidNeedle: String get() = textNeedle.filterNot { it == ':' || it == '-' || it.isWhitespace() }.uppercase()

    /** True when something narrows the list; drives the "clear filters" affordance. */
    val isFiltered: Boolean
        get() = textNeedle.isNotEmpty() || kinds.isNotEmpty() || outcome != OutcomeFilter.ALL || from != null || to != null

    fun matches(entry: HistoryEntry): Boolean =
        matchesText(entry) &&
            (kinds.isEmpty() || entry.kind in kinds) &&
            when (outcome) {
                OutcomeFilter.ALL -> true
                OutcomeFilter.SUCCESS -> entry.outcome.isSuccess
                OutcomeFilter.FAILED -> !entry.outcome.isSuccess
            } &&
            (from == null || entry.at >= from) &&
            (to == null || entry.at <= to)

    private fun matchesText(entry: HistoryEntry): Boolean {
        val needle = textNeedle
        if (needle.isEmpty()) return true
        if (entry.summary.contains(needle, ignoreCase = true)) return true
        if (entry.tagType?.contains(needle, ignoreCase = true) == true) return true
        val uid = uidNeedle
        return uid.isNotEmpty() && entry.uid?.toHex()?.contains(uid, ignoreCase = true) == true
    }

    companion object {
        const val DEFAULT_LIMIT = 500
    }
}
