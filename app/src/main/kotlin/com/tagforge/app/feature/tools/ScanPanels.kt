package com.tagforge.app.feature.tools

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.ui.components.RadarPulse
import com.tagforge.app.ui.components.StatusText
import com.tagforge.app.ui.text.TagErrorText
import com.tagforge.app.ui.text.messageText
import com.tagforge.app.ui.text.titleText
import com.tagforge.domain.tag.TagError

/** "Hold a tag to the phone": the radar plus a title/hint, faster while the tag is being read. */
@Composable
fun ScanListening(
    fast: Boolean,
    @StringRes waitingTitle: Int,
    @StringRes waitingHint: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        RadarPulse(fast = fast)
        if (fast) {
            StatusText(stringResource(R.string.scan_working_title), stringResource(R.string.scan_working_hint))
        } else {
            StatusText(stringResource(waitingTitle), stringResource(waitingHint))
        }
    }
}

/**
 * The error sits next to its retry control, not in a transient toast (AS-06). Errors that retrying cannot fix
 * (an unsupported card) offer "scan another tag" instead of "try again".
 */
@Composable
fun ScanErrorPanel(error: TagError, onRescan: () -> Unit, modifier: Modifier = Modifier) {
    val text = TagErrorText.of(error)
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        StatusText(title = text.titleText() ?: stringResource(R.string.scan_failure_title), supporting = text.messageText())
        if (error is TagError.Unsupported) {
            OutlinedButton(onClick = onRescan) { Text(stringResource(R.string.tools_scan_another)) }
        } else {
            Button(onClick = onRescan) { Text(stringResource(R.string.common_retry)) }
        }
    }
}
