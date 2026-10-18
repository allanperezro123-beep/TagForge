package com.tagforge.app.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.components.asciiOf
import com.tagforge.app.ui.theme.HexTextStyle
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ntag.Region

private const val HIDDEN_PAGE = "•• •• •• ••"
private const val BYTES_PER_LINE = 8
/** Rows open a detail view when tapped, so they keep the 48 dp minimum touch target (PR-04). */
private val RowHeightSingle = 48.dp
private val RowHeightDouble = 56.dp

/** Colour dots with names: the legend that makes the bands readable without relying on colour alone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegionLegend(regions: List<Region>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        regions.forEach { region ->
            FilterChip(
                selected = false,
                onClick = {},
                enabled = false,
                label = { Text(stringResource(region.labelRes()), style = MaterialTheme.typography.labelMedium) },
                leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(region.bandColor())) },
            )
        }
    }
}

@Composable
fun LayoutToggle(layout: DumpLayout, onChange: (DumpLayout) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(DumpLayout.PAGE_PER_ROW to R.string.dump_layout_page, DumpLayout.SIXTEEN_BYTES to R.string.dump_layout_16)
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, labelRes) ->
            SegmentedButton(
                selected = layout == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier.semantics { role = Role.RadioButton },
            ) { Text(stringResource(labelRes)) }
        }
    }
}

/** One tappable dump row: region band, page gutter, bytes, and (in the 4-byte layout) ASCII plus the region name. */
@Composable
fun DumpRowItem(row: DumpRow, layout: DumpLayout, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val band = row.primaryRegion?.bandColor() ?: Color.Transparent
    val regionName = row.primaryRegion?.let { stringResource(it.labelRes()) }
    val hex = row.bytes.toHex(" ")
    val description = if (row.hiddenByChip) {
        stringResource(R.string.dump_row_description_hidden, row.startPage, regionName ?: "")
    } else {
        stringResource(R.string.dump_row_description, row.startPage, regionName ?: "", hex)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (layout == DumpLayout.PAGE_PER_ROW) RowHeightSingle else RowHeightDouble)
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(band))
        Text("%02X".format(row.startPage), style = HexTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
        if (layout == DumpLayout.PAGE_PER_ROW) {
            Text(
                if (row.hiddenByChip) HIDDEN_PAGE else hex,
                style = HexTextStyle,
                color = if (row.hiddenByChip) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(if (row.hiddenByChip) "" else asciiOf(row.bytes), style = HexTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            regionName?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
            }
        } else {
            SixteenByteLines(row)
        }
    }
}

@Composable
private fun SixteenByteLines(row: DumpRow) {
    val bytes = row.bytes
    Column(Modifier.padding(vertical = 4.dp)) {
        for (start in 0 until bytes.size step BYTES_PER_LINE) {
            val line: Bytes = bytes.slice(start, minOf(start + BYTES_PER_LINE, bytes.size))
            Text(line.toHex(" "), style = HexTextStyle, color = MaterialTheme.colorScheme.onSurface)
        }
        if (row.hiddenByChip) {
            Text(stringResource(R.string.dump_hidden_note_short), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Explains the tapped row: which region each of its pages belongs to, what that means, and the bytes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageExplanationSheet(row: DumpRow, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val title = if (row.pages.size == 1) {
                stringResource(R.string.dump_sheet_page, row.startPage)
            } else {
                stringResource(R.string.dump_sheet_pages, row.pages.first(), row.pages.last())
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
            row.pages.forEachIndexed { index, page ->
                val regions = row.regions[index]
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (row.pages.size > 1) Text(stringResource(R.string.dump_sheet_page, page), style = MaterialTheme.typography.titleSmall)
                    val pageBytes = row.bytes.slice(index * 4, index * 4 + 4)
                    val hidden = regions.any { it.hiddenByChip }
                    Text(if (hidden) HIDDEN_PAGE else pageBytes.toHex(" "), style = HexTextStyle)
                    regions.forEach { region ->
                        Text(stringResource(region.labelRes()), style = MaterialTheme.typography.titleSmall, color = region.bandColor())
                        Text(stringResource(region.explanationRes()), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (regions.isEmpty()) Text(stringResource(R.string.dump_sheet_no_region), style = MaterialTheme.typography.bodyMedium)
                    if (hidden) {
                        Text(stringResource(R.string.dump_hidden_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
