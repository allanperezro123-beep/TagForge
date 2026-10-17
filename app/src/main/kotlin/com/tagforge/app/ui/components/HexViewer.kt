package com.tagforge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.theme.HexTextStyle
import com.tagforge.domain.common.Bytes

private const val FIRST_PRINTABLE = 0x20
private const val LAST_PRINTABLE = 0x7E

/** One page of memory. [band] colours a thin strip on the left to mark a region (used by the annotated dump). */
data class HexRowUi(val page: Int, val bytes: Bytes, val band: Color? = null)

/**
 * Page-per-row hex + ASCII view in a monospace face. Each row is a single accessibility node reading
 * "Page 4, bytes 03 1A D1 01" rather than three separate fragments.
 */
@Composable
fun HexViewer(
    rows: List<HexRowUi>,
    modifier: Modifier = Modifier,
    footerNote: String? = null,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(rows, key = { it.page }) { row -> HexRowItem(row) }
        if (footerNote != null) {
            item(key = "footer") {
                Text(
                    footerNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun HexRowItem(row: HexRowUi) {
    val description = stringResource(R.string.hex_row_description, row.page, row.bytes.toHex(" "))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(row.band ?: Color.Transparent),
        )
        Text("%02X".format(row.page), style = HexTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
        Text(row.bytes.toHex(" "), style = HexTextStyle, color = MaterialTheme.colorScheme.onSurface)
        Text(asciiOf(row.bytes), style = HexTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun asciiOf(bytes: Bytes): String = buildString {
    for (i in 0 until bytes.size) {
        val value = bytes[i]
        append(if (value in FIRST_PRINTABLE..LAST_PRINTABLE) value.toChar() else '.')
    }
}
