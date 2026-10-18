package com.tagforge.app.infrastructure.persistence

import com.tagforge.domain.history.HistoryQuery
import com.tagforge.domain.history.OutcomeFilter

/**
 * A [HistoryQuery] flattened into the bind arguments of `HistoryDao.observe`. Every "no filter" case is a value that
 * matches everything, so a single prepared statement serves all queries (and the IN list is never empty).
 * [text] and [uid] are already LIKE-escaped with `\`.
 */
data class HistoryQueryArgs(
    val text: String,
    val uid: String,
    val allKinds: Boolean,
    val kinds: List<String>,
    val outcome: Int,
    val from: Long,
    val to: Long,
    val limit: Int,
) {
    companion object {
        const val OUTCOME_ANY = 0
        const val OUTCOME_SUCCESS = 1
        const val OUTCOME_FAILED = 2
    }
}

fun HistoryQuery.toArgs(): HistoryQueryArgs = HistoryQueryArgs(
    text = escapeLike(textNeedle),
    uid = escapeLike(uidNeedle),
    allKinds = kinds.isEmpty(),
    kinds = if (kinds.isEmpty()) listOf("") else kinds.map { it.code },
    outcome = when (outcome) {
        OutcomeFilter.ALL -> HistoryQueryArgs.OUTCOME_ANY
        OutcomeFilter.SUCCESS -> HistoryQueryArgs.OUTCOME_SUCCESS
        OutcomeFilter.FAILED -> HistoryQueryArgs.OUTCOME_FAILED
    },
    from = from?.toEpochMilliseconds() ?: Long.MIN_VALUE,
    to = to?.toEpochMilliseconds() ?: Long.MAX_VALUE,
    limit = limit,
)

/** Makes `%`, `_` and the escape character itself literal; the DAO declares `ESCAPE '\'`. */
internal fun escapeLike(text: String): String =
    text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
