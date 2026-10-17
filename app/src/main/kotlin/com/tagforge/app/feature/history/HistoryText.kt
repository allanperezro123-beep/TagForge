package com.tagforge.app.feature.history

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tagforge.app.R
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryOutcome
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Instant

@StringRes
fun HistoryKind.labelRes(): Int = when (this) {
    HistoryKind.READ -> R.string.history_kind_read
    HistoryKind.WRITE -> R.string.history_kind_write
    HistoryKind.ERASE -> R.string.history_kind_erase
    HistoryKind.FORMAT -> R.string.history_kind_format
    HistoryKind.CLONE -> R.string.history_kind_clone
    HistoryKind.BATCH -> R.string.history_kind_batch
}

@StringRes
fun HistoryOutcome.labelRes(): Int = when (this) {
    HistoryOutcome.SUCCESS -> R.string.history_outcome_success
    HistoryOutcome.FAILED_TAGLOST -> R.string.history_outcome_failed_taglost
    HistoryOutcome.FAILED_READ_ONLY -> R.string.history_outcome_failed_read_only
    HistoryOutcome.FAILED_CAPACITY -> R.string.history_outcome_failed_capacity
    HistoryOutcome.FAILED_VERIFY -> R.string.history_outcome_failed_verify
    HistoryOutcome.FAILED_AUTH -> R.string.history_outcome_failed_auth
    HistoryOutcome.FAILED_OTHER -> R.string.history_outcome_failed_other
}

/** "Oct 16, 2026, 3:04 PM" in the user's language and time zone. */
@Composable
fun rememberDateText(at: Instant): String {
    val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.getDefault()) }
    return remember(at) { formatter.format(java.time.Instant.ofEpochMilli(at.toEpochMilliseconds()).atZone(ZoneId.systemDefault())) }
}
