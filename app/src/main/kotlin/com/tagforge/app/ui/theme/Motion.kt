package com.tagforge.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

object Motion {
    /** Smooth deceleration for entering elements. */
    val Glide = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Slight overshoot for success confirmations. */
    val Overshoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    const val RadarLoopMillis = 1800
    const val RadarStaggerMillis = 600
    const val ConfirmMillis = 300
}

/** True when the user turned animations off (developer option or accessibility), so decorative motion must stop. */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
