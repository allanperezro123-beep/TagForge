package com.tagforge.app.feature.tools.security

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tagforge.app.R
import com.tagforge.app.feature.tools.ToolSheetUi
import com.tagforge.app.ui.text.TagErrorText
import com.tagforge.app.ui.text.messageText
import com.tagforge.app.ui.text.titleText
import com.tagforge.domain.scan.OperationPhase

/** Resolves a [SecuritySheet] into the strings the shared scan sheet shows. */
@Composable
fun SecuritySheet.toUi(): ToolSheetUi = when (this) {
    is SecuritySheet.Waiting -> when (stage) {
        SecurityStage.READ -> ToolSheetUi.Waiting(stringResource(R.string.security_sheet_read_waiting), stringResource(R.string.security_sheet_read_hint))
        SecurityStage.EXECUTE -> ToolSheetUi.Waiting(stringResource(R.string.security_sheet_exec_waiting), stringResource(R.string.security_sheet_exec_hint))
    }
    is SecuritySheet.Working -> ToolSheetUi.Working(
        title = stringResource(if (phase == OperationPhase.VERIFYING) R.string.security_sheet_verifying else R.string.security_sheet_working),
        hint = stringResource(R.string.tools_sheet_keep_still),
    )
    is SecuritySheet.Success -> ToolSheetUi.Success(
        title = stringResource(action.doneTitle()),
        detail = pack?.let { stringResource(unlockDetail(packMatches), it.toHex(" ")) }
            ?: stringResource(R.string.security_sheet_done_detail),
    )
    is SecuritySheet.Failure -> {
        val text = TagErrorText.of(error)
        ToolSheetUi.Failure(
            title = text.titleText() ?: stringResource(R.string.security_sheet_failure_title),
            message = text.messageText(),
            hint = null,
            canRetry = canRetry,
        )
    }
}

private fun unlockDetail(packMatches: Boolean?): Int = when (packMatches) {
    true -> R.string.security_sheet_done_unlock_match
    false -> R.string.security_sheet_done_unlock_mismatch
    null -> R.string.security_sheet_done_unlock_detail
}

private fun SecurityAction.doneTitle(): Int = when (this) {
    SecurityAction.SET_PASSWORD -> R.string.security_sheet_done_set_password
    SecurityAction.REMOVE_PASSWORD -> R.string.security_sheet_done_remove_password
    SecurityAction.CONFIG_LOCK -> R.string.security_sheet_done_config_lock
    SecurityAction.LOCK -> R.string.security_sheet_done_lock
    SecurityAction.UNLOCK -> R.string.security_sheet_done_unlock
}
