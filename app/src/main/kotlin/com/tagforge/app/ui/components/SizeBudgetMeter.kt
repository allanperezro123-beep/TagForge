package com.tagforge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.theme.extendedColors
import com.tagforge.domain.ndef.BudgetReport

private const val WARNING_THRESHOLD = 0.8f
private const val MIN_SEGMENT_WEIGHT = 0.5f
private val BAR_HEIGHT = 12.dp
private val SEGMENT_GAP = 2.dp

private enum class MeterLevel { OK, WARNING, OVER }

/**
 * How full the tag will be: one bar segment per record, green-ish primary below 80%, warning up to 100%, error beyond.
 * The state is also written out ("Fits", "Almost full", "Too large") with an icon, so colour is never the only signal.
 * When it does not fit, the fix is spelled out next to the bar ([suggestedChipName] is the smallest tag that would).
 */
@Composable
fun SizeBudgetMeter(
    budget: BudgetReport,
    targetName: String,
    suggestedChipName: String?,
    modifier: Modifier = Modifier,
) {
    val fraction = budget.usedFraction
    val level = when {
        fraction > 1f || !budget.fits -> MeterLevel.OVER
        fraction >= WARNING_THRESHOLD -> MeterLevel.WARNING
        else -> MeterLevel.OK
    }
    val color = when (level) {
        MeterLevel.OK -> MaterialTheme.colorScheme.primary
        MeterLevel.WARNING -> MaterialTheme.extendedColors.warning
        MeterLevel.OVER -> MaterialTheme.colorScheme.error
    }
    val stateText = stringResource(
        when (level) {
            MeterLevel.OK -> R.string.write_budget_state_ok
            MeterLevel.WARNING -> R.string.write_budget_state_warning
            MeterLevel.OVER -> R.string.write_budget_state_over
        },
    )
    val description = stringResource(R.string.write_budget_description, budget.used, budget.capacity, stateText)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.write_budget_value, budget.used, budget.capacity), style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Icon(levelIcon(level), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Text(stateText, style = MaterialTheme.typography.labelLarge, color = color)
            }
        }

        SegmentedBar(budget, color, Modifier.semantics { contentDescription = description })

        Text(
            stringResource(R.string.write_budget_overhead, budget.tlvOverhead),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (level == MeterLevel.OVER) {
            Text(
                text = if (suggestedChipName != null) {
                    stringResource(R.string.write_budget_over_suggest, budget.overBy, targetName, suggestedChipName)
                } else {
                    stringResource(R.string.write_budget_over_none, budget.overBy, targetName)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SegmentedBar(budget: BudgetReport, color: Color, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val free = (budget.capacity - budget.used).coerceAtLeast(0)
    Row(
        modifier = modifier.fillMaxWidth().height(BAR_HEIGHT).clip(RoundedCornerShape(BAR_HEIGHT / 2)).background(track),
    ) {
        budget.perRecord.forEachIndexed { index, size ->
            if (index > 0) Spacer(Modifier.width(SEGMENT_GAP).fillMaxHeight().background(track))
            Box(
                Modifier
                    .weight(size.toFloat().coerceAtLeast(MIN_SEGMENT_WEIGHT))
                    .fillMaxHeight()
                    .background(if (index % 2 == 0) color else color.copy(alpha = SECOND_SEGMENT_ALPHA))
                    .clearAndSetSemantics {},
            )
        }
        if (free > 0) Spacer(Modifier.weight(free.toFloat()).fillMaxHeight())
    }
}

private const val SECOND_SEGMENT_ALPHA = 0.65f

private fun levelIcon(level: MeterLevel): ImageVector = when (level) {
    MeterLevel.OK -> Icons.Outlined.CheckCircle
    MeterLevel.WARNING -> Icons.Outlined.WarningAmber
    MeterLevel.OVER -> Icons.Outlined.ErrorOutline
}
