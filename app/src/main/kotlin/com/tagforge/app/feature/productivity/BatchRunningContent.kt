package com.tagforge.app.feature.productivity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.components.RadarPulse
import com.tagforge.app.ui.text.TagErrorText
import com.tagforge.app.ui.text.messageText
import com.tagforge.domain.exchange.BatchSummary
import com.tagforge.domain.scan.OperationPhase

/** The batch is running: the number the next tag gets, what just happened, and the counters (PR-01 summary). */
@Composable
fun BatchRunningContent(
    state: BatchUiState,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val run = state.run ?: return
    val working = state.status is BatchStatus.Working
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RadarPulse(fast = working)
        Text(stringResource(R.string.batch_next_tag), style = MaterialTheme.typography.titleMedium)
        Text(run.nextLabel, style = MaterialTheme.typography.displayMedium)
        StatusLine(state.status)
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
        SummaryCard(run.summary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onSkip, enabled = !working, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.batch_skip))
            }
            Button(onClick = onStop, enabled = !working, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.batch_stop))
            }
        }
    }
}

/** The final tally once the batch is complete or was stopped. */
@Composable
fun BatchDoneContent(state: BatchUiState, onNewBatch: () -> Unit, modifier: Modifier = Modifier) {
    val run = state.run ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(if (run.isComplete) R.string.batch_done_complete else R.string.batch_done_stopped),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        SummaryCard(run.summary)
        Button(onClick = onNewBatch, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.batch_new)) }
    }
}

@Composable
private fun StatusLine(status: BatchStatus) {
    val text = when (status) {
        BatchStatus.Waiting -> stringResource(R.string.batch_status_waiting)
        is BatchStatus.Working -> stringResource(status.phase.titleRes())
        is BatchStatus.Written -> stringResource(R.string.batch_status_written, status.label)
        is BatchStatus.Failed -> stringResource(R.string.batch_status_failed, status.label, TagErrorText.of(status.error).messageText())
    }
    val failed = status is BatchStatus.Failed
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun SummaryCard(summary: BatchSummary) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.batch_summary, summary.ok, summary.failed, summary.remaining),
                style = MaterialTheme.typography.titleMedium,
            )
            if (summary.skipped > 0) Text(stringResource(R.string.batch_summary_skipped, summary.skipped))
        }
    }
}

private fun OperationPhase.titleRes(): Int = when (this) {
    OperationPhase.CONNECTING, OperationPhase.READING -> R.string.write_sheet_connecting
    OperationPhase.WRITING -> R.string.write_sheet_writing
    OperationPhase.VERIFYING -> R.string.write_sheet_verifying
}
