package com.tagforge.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tagforge.app.ui.theme.Motion
import com.tagforge.app.ui.theme.rememberReducedMotion

private const val RING_COUNT = 3
private const val MIN_RING_SCALE = 0.35f
private const val MAX_RING_ALPHA = 0.5f

/**
 * Decorative "listening" animation: three rings expanding from the glyph. [fast] doubles the speed while a tag is
 * being worked on. With animations disabled system-wide it is a static glyph with one faint ring.
 * It carries no semantics; the surrounding status text is the accessible announcement.
 */
@Composable
fun RadarPulse(
    modifier: Modifier = Modifier,
    fast: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val reducedMotion = rememberReducedMotion()
    Box(modifier = modifier.size(160.dp), contentAlignment = Alignment.Center) {
        if (reducedMotion) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(tint.copy(alpha = 0.25f), radius = size.minDimension / 2 * 0.7f, style = Stroke(3.dp.toPx()))
            }
        } else {
            val transition = rememberInfiniteTransition(label = "radar")
            val loop = if (fast) Motion.RadarLoopMillis / 2 else Motion.RadarLoopMillis
            val stagger = if (fast) Motion.RadarStaggerMillis / 2 else Motion.RadarStaggerMillis
            val progress = List(RING_COUNT) { index ->
                transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = loop, easing = Motion.Glide),
                        repeatMode = RepeatMode.Restart,
                        initialStartOffset = StartOffset(index * stagger),
                    ),
                    label = "ring$index",
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                val maxRadius = size.minDimension / 2
                progress.forEach { ring ->
                    val p = ring.value
                    val radius = maxRadius * (MIN_RING_SCALE + (1f - MIN_RING_SCALE) * p)
                    drawCircle(tint.copy(alpha = MAX_RING_ALPHA * (1f - p)), radius = radius, style = Stroke(3.dp.toPx()))
                }
            }
        }
        Icon(imageVector = Icons.Outlined.Nfc, contentDescription = null, modifier = Modifier.size(48.dp), tint = tint)
    }
}
