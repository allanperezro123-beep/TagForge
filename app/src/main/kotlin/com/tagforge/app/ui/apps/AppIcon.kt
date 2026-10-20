package com.tagforge.app.ui.apps

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ICON_PIXELS = 96
private const val CACHE_ENTRIES = 256

/** Decoded launcher icons, shared by every row and every open of the picker; a scrolled-away row costs nothing to redraw. */
private val iconCache = LruCache<String, ImageBitmap>(CACHE_ENTRIES)

/** The launcher icon of [packageName], decoded on a background thread and cached. */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val bitmap by produceState(iconCache.get(packageName), packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(ICON_PIXELS, ICON_PIXELS).asImageBitmap() }.getOrNull()
            }?.also { iconCache.put(packageName, it) }
        }
    }
    val icon = bitmap
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = modifier.size(size))
    } else {
        Icon(Icons.Outlined.Android, contentDescription = null, modifier = modifier.size(size), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
