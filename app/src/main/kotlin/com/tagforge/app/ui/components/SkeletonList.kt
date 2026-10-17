package com.tagforge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tagforge.app.R

/**
 * Placeholder rows with the FINAL row height, so swapping them for real content causes no layout shift (AS-06).
 */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    rowCount: Int = 5,
    rowHeight: Dp = 72.dp,
) {
    val loading = stringResource(R.string.common_loading)
    Column(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(rowCount) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.medium),
            )
        }
    }
}
