package com.tagforge.app.feature.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.components.ConfirmDialog
import com.tagforge.app.ui.components.NfcStatusBanner
import com.tagforge.app.ui.text.TagErrorText
import com.tagforge.app.ui.text.messageText
import com.tagforge.app.ui.text.titleText
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.tag.TagError

/** Everything the Tools hub can do, as plain lambdas so the screen renders without a ViewModel (AS-05). */
data class ToolsActions(
    val onErase: () -> Unit,
    val onFormat: () -> Unit,
    val onDump: () -> Unit,
    val onClone: () -> Unit,
    val onTech: () -> Unit,
    val onConfirm: () -> Unit,
    val onCancelConfirm: () -> Unit,
    val onSheetDismissed: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenNfcSettings: () -> Unit,
)

/** The Tools hub (TT-01, TT-02 entry points) with the destructive confirmation and the scan sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(state: ToolsUiState, actions: ToolsActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tools_title)) }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            NfcStatusBanner(state.availability, actions.onOpenNfcSettings, Modifier.padding(16.dp))

            SectionHeader(R.string.tools_section_change)
            ToolEntry(Icons.Outlined.DeleteForever, R.string.tools_erase, R.string.tools_erase_summary, state.canStart, destructive = true, onClick = actions.onErase)
            ToolEntry(Icons.Outlined.CleaningServices, R.string.tools_format, R.string.tools_format_summary, state.canStart, destructive = true, onClick = actions.onFormat)

            SectionHeader(R.string.tools_section_inspect)
            ToolEntry(Icons.Outlined.Memory, R.string.tools_dump, R.string.tools_dump_summary, true, onClick = actions.onDump)
            ToolEntry(Icons.Outlined.ContentCopy, R.string.tools_clone, R.string.tools_clone_summary, true, onClick = actions.onClone)
            ToolEntry(Icons.Outlined.Sensors, R.string.tools_tech, R.string.tools_tech_summary, true, onClick = actions.onTech)
        }
    }

    state.confirm?.let { tool ->
        ConfirmDialog(
            title = stringResource(if (tool == DestructiveTool.ERASE) R.string.tools_erase_confirm_title else R.string.tools_format_confirm_title),
            body = stringResource(if (tool == DestructiveTool.ERASE) R.string.tools_erase_confirm_body else R.string.tools_format_confirm_body),
            confirmLabel = stringResource(if (tool == DestructiveTool.ERASE) R.string.tools_erase_confirm_action else R.string.tools_format_confirm_action),
            onConfirm = actions.onConfirm,
            onDismiss = actions.onCancelConfirm,
            destructive = true,
        )
    }

    state.sheet?.let { sheet ->
        ToolScanSheet(ui = sheet.toUi(), onDismiss = actions.onSheetDismissed, onRetry = actions.onRetry)
    }
}

@Composable
private fun SectionHeader(titleRes: Int) {
    Text(
        stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** A disabled entry says why in its summary (NFC off banner above), and the destructive ones wear the error colour. */
@Composable
private fun ToolEntry(
    icon: ImageVector,
    titleRes: Int,
    summaryRes: Int,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null, tint = if (enabled) tint else MaterialTheme.colorScheme.outline) },
        headlineContent = { Text(stringResource(titleRes), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline) },
        supportingContent = { Text(stringResource(summaryRes)) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
    )
}

@Composable
private fun ToolSheet.toUi(): ToolSheetUi = when (this) {
    is ToolSheet.Waiting -> ToolSheetUi.Waiting(
        title = stringResource(if (tool == DestructiveTool.ERASE) R.string.tools_sheet_erase_waiting else R.string.tools_sheet_format_waiting),
        hint = stringResource(R.string.tools_sheet_waiting_hint),
    )
    is ToolSheet.Working -> ToolSheetUi.Working(
        title = stringResource(
            when (phase) {
                OperationPhase.CONNECTING, OperationPhase.READING -> R.string.tools_sheet_connecting
                OperationPhase.WRITING -> if (tool == DestructiveTool.ERASE) R.string.tools_sheet_erasing else R.string.tools_sheet_formatting
                OperationPhase.VERIFYING -> R.string.tools_sheet_verifying
            },
        ),
        hint = stringResource(R.string.tools_sheet_keep_still),
    )
    is ToolSheet.Success -> {
        val (title, detail) = when {
            tool == DestructiveTool.ERASE && changed -> R.string.tools_erase_done to R.string.tools_erase_done_detail
            tool == DestructiveTool.ERASE -> R.string.tools_erase_nothing to R.string.tools_erase_nothing_detail
            changed -> R.string.tools_format_done to R.string.tools_format_done_detail
            else -> R.string.tools_format_nothing to R.string.tools_format_nothing_detail
        }
        ToolSheetUi.Success(title = stringResource(title), detail = stringResource(detail))
    }
    is ToolSheet.Failure -> {
        val text = TagErrorText.of(error)
        ToolSheetUi.Failure(
            title = text.titleText() ?: stringResource(R.string.tools_failure_title),
            message = text.messageText(),
            hint = failureHint(tool, error)?.let { stringResource(it) },
            canRetry = canRetry,
        )
    }
}

/** Guidance for the failures where the fix is not obvious. A tag lost mid-way is never left half written. */
private fun failureHint(tool: DestructiveTool, error: TagError): Int? = when {
    error == TagError.TagLost -> R.string.tools_hint_taglost
    error == TagError.ReadOnly -> R.string.tools_hint_read_only
    error == TagError.NotNdef && tool == DestructiveTool.ERASE -> R.string.tools_hint_use_format
    error is TagError.VerifyMismatch -> R.string.tools_hint_verify
    else -> null
}
