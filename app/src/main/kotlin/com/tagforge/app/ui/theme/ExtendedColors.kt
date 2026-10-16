package com.tagforge.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colours Material 3 has no slot for. Both are text-safe on surface (>= 4.5:1). */
@Immutable
data class ExtendedColors(val success: Color, val warning: Color)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(success = Color(ForgePalette.light.success), warning = Color(ForgePalette.light.warning))
}
