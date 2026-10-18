package com.tagforge.app.feature.productivity

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.tagforge.app.R
import com.tagforge.domain.exchange.EntryProblem
import com.tagforge.domain.exchange.ExchangeSection
import com.tagforge.domain.exchange.ImportError

/** The sentence for an outcome. A rejected file always says that nothing was imported. */
@Composable
fun ImportExportMessage.text(): String = when (this) {
    is ImportExportMessage.Exported -> pluralStringResource(R.plurals.import_export_exported, itemCount, itemCount)
    ImportExportMessage.ExportFailed -> stringResource(R.string.import_export_export_failed)
    is ImportExportMessage.Imported -> stringResource(R.string.import_export_imported, report.imported, report.skipped)
    is ImportExportMessage.ImportRejected -> error.text()
    ImportExportMessage.ImportStorageFailed -> stringResource(R.string.import_export_storage_failed)
    ImportExportMessage.ImportTooLarge -> stringResource(R.string.import_export_too_large)
    ImportExportMessage.ImportUnreadable -> stringResource(R.string.import_export_unreadable)
}

@Composable
private fun ImportError.text(): String = when (this) {
    ImportError.NotJson -> stringResource(R.string.import_export_rejected_not_json)
    ImportError.NotABundle -> stringResource(R.string.import_export_rejected_not_bundle)
    is ImportError.UnsupportedSchema -> stringResource(R.string.import_export_rejected_schema, found)
    is ImportError.TooMany -> stringResource(R.string.import_export_rejected_too_many, stringResource(section.labelRes()))
    is ImportError.BadEntry -> stringResource(
        R.string.import_export_rejected_entry,
        stringResource(section.labelRes()),
        number,
        stringResource(problem.labelRes()),
    )
}

@StringRes
private fun ExchangeSection.labelRes(): Int = when (this) {
    ExchangeSection.HISTORY -> R.string.import_export_section_history
    ExchangeSection.TEMPLATES -> R.string.import_export_section_templates
    ExchangeSection.TASKS -> R.string.import_export_section_tasks
}

@StringRes
private fun EntryProblem.labelRes(): Int = when (this) {
    EntryProblem.BAD_STRUCTURE -> R.string.import_problem_bad_structure
    EntryProblem.UNKNOWN_KIND -> R.string.import_problem_unknown_kind
    EntryProblem.UNKNOWN_OUTCOME -> R.string.import_problem_unknown_outcome
    EntryProblem.BAD_HEX -> R.string.import_problem_bad_hex
    EntryProblem.UNREADABLE_NDEF -> R.string.import_problem_unreadable_ndef
    EntryProblem.BLOCKED_ACTION -> R.string.import_problem_blocked_action
    EntryProblem.INVALID_VALUE -> R.string.import_problem_invalid_value
    EntryProblem.TOO_LONG -> R.string.import_problem_too_long
}
