package com.tagforge.app.feature.taskrunner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.text.TaskText
import com.tagforge.app.ui.theme.extendedColors
import com.tagforge.domain.task.ActionCheck
import com.tagforge.domain.task.ActionOutcome
import com.tagforge.domain.task.Capability
import com.tagforge.domain.task.PlanItem
import com.tagforge.domain.task.TaskRisk

/**
 * One action of a task in plain language with its full parameters, a visible badge when it is one of the risky kinds,
 * and either what to expect before running ([status] null) or how it went. Blocked actions say so and never run.
 * Every state is an icon AND words, never colour alone.
 */
@Composable
fun TaskActionRow(
    index: Int,
    total: Int,
    item: PlanItem,
    status: RowStatus?,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val description = TaskText.describe(resources, item.action)
    val position = stringResource(R.string.task_runner_action_number, index + 1, total)
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp).semantics { contentDescription = "$position. $description" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(description, style = MaterialTheme.typography.bodyLarge)
        RiskBadge(item.risk)

        val check = item.check
        when {
            check is ActionCheck.Blocked -> StatusLine(Icons.Outlined.Block, MaterialTheme.colorScheme.error, stringResource(TaskText.blocked(check.reason)))
            status == null -> PreRunNote(item.capability, onFix)
            else -> RunStatus(status, onFix)
        }
    }
}

@Composable
private fun PreRunNote(capability: Capability, onFix: () -> Unit) {
    val note = TaskText.note(LocalResources.current, capability) ?: return
    val tint = if (capability is Capability.Unavailable) MaterialTheme.colorScheme.error else MaterialTheme.extendedColors.warning
    StatusLine(Icons.Outlined.WarningAmber, tint, note)
    if (capability is Capability.NeedsGrant) FixButton(onFix)
}

@Composable
private fun RunStatus(status: RowStatus, onFix: () -> Unit) {
    when (status) {
        RowStatus.Pending -> StatusLine(Icons.Outlined.HourglassEmpty, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.task_runner_pending))
        RowStatus.Running -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.task_runner_working), style = MaterialTheme.typography.bodyMedium)
        }
        is RowStatus.Finished -> {
            val outcome = status.outcome
            val (icon, tint) = outcomeStyle(outcome)
            StatusLine(icon, tint, TaskText.outcome(LocalResources.current, outcome))
            if (outcome is ActionOutcome.PermissionNeeded) FixButton(onFix)
        }
    }
}

@Composable
private fun outcomeStyle(outcome: ActionOutcome): Pair<ImageVector, Color> = when (outcome) {
    is ActionOutcome.Done -> Icons.Outlined.CheckCircle to MaterialTheme.extendedColors.success
    is ActionOutcome.OpenedFallback -> Icons.Outlined.WarningAmber to MaterialTheme.extendedColors.warning
    is ActionOutcome.PermissionNeeded -> Icons.Outlined.WarningAmber to MaterialTheme.extendedColors.warning
    is ActionOutcome.Skipped -> Icons.Outlined.Block to MaterialTheme.colorScheme.onSurfaceVariant
    is ActionOutcome.Failed -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.error
}

@Composable
private fun StatusLine(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

@Composable
private fun FixButton(onFix: () -> Unit) {
    val label = stringResource(R.string.task_runner_fix)
    val description = stringResource(R.string.task_runner_fix_description)
    TextButton(
        onClick = onFix,
        modifier = Modifier.defaultMinSize(minHeight = 48.dp).semantics { contentDescription = description },
    ) { Text(label) }
}

/** "Needs care" marker for the six elevated-risk actions: warning icon plus the kind, so colour is never the only cue. */
@Composable
fun RiskBadge(risk: TaskRisk, modifier: Modifier = Modifier) {
    val label = TaskText.risk(risk)?.let { stringResource(it) } ?: return
    val description = stringResource(R.string.task_badge_description, label)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
