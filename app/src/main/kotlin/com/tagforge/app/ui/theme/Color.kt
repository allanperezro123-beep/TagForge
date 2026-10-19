package com.tagforge.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** Palette as plain ARGB longs so contrast can be unit-tested without Compose. */
data class PaletteSpec(
    val primary: Long, val onPrimary: Long, val primaryContainer: Long, val onPrimaryContainer: Long,
    val secondary: Long, val onSecondary: Long, val secondaryContainer: Long, val onSecondaryContainer: Long,
    val tertiary: Long, val onTertiary: Long, val tertiaryContainer: Long, val onTertiaryContainer: Long,
    val error: Long, val onError: Long, val errorContainer: Long, val onErrorContainer: Long,
    val background: Long, val onBackground: Long,
    val surface: Long, val onSurface: Long, val surfaceVariant: Long, val onSurfaceVariant: Long,
    val surfaceDim: Long, val surfaceBright: Long,
    val surfaceContainerLowest: Long, val surfaceContainerLow: Long, val surfaceContainer: Long,
    val surfaceContainerHigh: Long, val surfaceContainerHighest: Long,
    val outline: Long, val outlineVariant: Long,
    val inverseSurface: Long, val inverseOnSurface: Long, val inversePrimary: Long,
    val success: Long, val warning: Long,
)

/**
 * The Tag Forge brand palette (branding/palette.md): flat, calm blue-slate with a cyan accent, tonal surfaces and no pure
 * black. It is the default look; dynamic colour (Material You) is an opt-in on API 31+. Every text pair is checked at 4.5:1
 * by ContrastTest and BrandPaletteContrastTest, and every role is pinned to the palette table by BrandPaletteRolesTest.
 *
 * Not in palette.md, so derived here: surfaceDim/surfaceBright (one tonal step beyond the container ramp) and the
 * semantic success/warning pair (the brand has no green/amber; both stay text-safe on every surface).
 */
object ForgePalette {
    val light = PaletteSpec(
        primary = 0xFF1D55D0, onPrimary = 0xFFFFFFFF, primaryContainer = 0xFFD5E4FE, onPrimaryContainer = 0xFF001454,
        secondary = 0xFF526072, onSecondary = 0xFFFFFFFF, secondaryContainer = 0xFFD4E4FA, onSecondaryContainer = 0xFF101C2C,
        tertiary = 0xFF027080, onTertiary = 0xFFFFFFFF, tertiaryContainer = 0xFFB6F2FE, onTertiaryContainer = 0xFF011F25,
        error = 0xFFB3261E, onError = 0xFFFFFFFF, errorContainer = 0xFFF9DEDC, onErrorContainer = 0xFF410E0B,
        background = 0xFFF6F9FF, onBackground = 0xFF181B20,
        surface = 0xFFF6F9FF, onSurface = 0xFF181B20, surfaceVariant = 0xFFDCE3EF, onSurfaceVariant = 0xFF414851,
        surfaceDim = 0xFFD6DAE1, surfaceBright = 0xFFF6F9FF,
        surfaceContainerLowest = 0xFFFFFFFF, surfaceContainerLow = 0xFFEFF4FA, surfaceContainer = 0xFFEAEEF5,
        surfaceContainerHigh = 0xFFE4E9EF, surfaceContainerHighest = 0xFFDEE2E9,
        outline = 0xFF717882, outlineVariant = 0xFFBFC7D3,
        inverseSurface = 0xFF2D3135, inverseOnSurface = 0xFFEDF1F7, inversePrimary = 0xFFAAC8FF,
        success = 0xFF2E6B3A, warning = 0xFF7A5A00,
    )

    val dark = PaletteSpec(
        primary = 0xFF6F9EFE, onPrimary = 0xFF001454, primaryContainer = 0xFF234387, onPrimaryContainer = 0xFFD5E4FE,
        secondary = 0xFFB8C7DD, onSecondary = 0xFF253142, secondaryContainer = 0xFF3A4859, onSecondaryContainer = 0xFFD4E4FA,
        tertiary = 0xFF58D6EE, onTertiary = 0xFF00363E, tertiaryContainer = 0xFF004E5A, onTertiaryContainer = 0xFF9FEEFF,
        error = 0xFFF2B8B5, onError = 0xFF601410, errorContainer = 0xFF8C1D18, onErrorContainer = 0xFFF9DEDC,
        background = 0xFF0E1622, onBackground = 0xFFDEE2E9,
        surface = 0xFF0E1622, onSurface = 0xFFDEE2E9, surfaceVariant = 0xFF3F4854, onSurfaceVariant = 0xFFBFC7D3,
        surfaceDim = 0xFF0E1622, surfaceBright = 0xFF333A46,
        surfaceContainerLowest = 0xFF080E19, surfaceContainerLow = 0xFF141B26, surfaceContainer = 0xFF19202B,
        surfaceContainerHigh = 0xFF212934, surfaceContainerHighest = 0xFF29303C,
        outline = 0xFF8A929C, outlineVariant = 0xFF3F4854,
        inverseSurface = 0xFFDEE2E9, inverseOnSurface = 0xFF2D3135, inversePrimary = 0xFF1D55D0,
        success = 0xFF9BD5A0, warning = 0xFFF1C24A,
    )
}

fun PaletteSpec.toLightColorScheme(): ColorScheme = lightColorScheme(
    primary = Color(primary), onPrimary = Color(onPrimary),
    primaryContainer = Color(primaryContainer), onPrimaryContainer = Color(onPrimaryContainer),
    secondary = Color(secondary), onSecondary = Color(onSecondary),
    secondaryContainer = Color(secondaryContainer), onSecondaryContainer = Color(onSecondaryContainer),
    tertiary = Color(tertiary), onTertiary = Color(onTertiary),
    tertiaryContainer = Color(tertiaryContainer), onTertiaryContainer = Color(onTertiaryContainer),
    error = Color(error), onError = Color(onError),
    errorContainer = Color(errorContainer), onErrorContainer = Color(onErrorContainer),
    background = Color(background), onBackground = Color(onBackground),
    surface = Color(surface), onSurface = Color(onSurface),
    surfaceVariant = Color(surfaceVariant), onSurfaceVariant = Color(onSurfaceVariant),
    surfaceDim = Color(surfaceDim), surfaceBright = Color(surfaceBright),
    surfaceContainerLowest = Color(surfaceContainerLowest), surfaceContainerLow = Color(surfaceContainerLow),
    surfaceContainer = Color(surfaceContainer), surfaceContainerHigh = Color(surfaceContainerHigh),
    surfaceContainerHighest = Color(surfaceContainerHighest),
    outline = Color(outline), outlineVariant = Color(outlineVariant),
    inverseSurface = Color(inverseSurface), inverseOnSurface = Color(inverseOnSurface),
    inversePrimary = Color(inversePrimary),
    // Flat cards: elevation is the tonal surfaceContainer ramp, never a blue wash.
    surfaceTint = Color.Transparent,
)

fun PaletteSpec.toDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = Color(primary), onPrimary = Color(onPrimary),
    primaryContainer = Color(primaryContainer), onPrimaryContainer = Color(onPrimaryContainer),
    secondary = Color(secondary), onSecondary = Color(onSecondary),
    secondaryContainer = Color(secondaryContainer), onSecondaryContainer = Color(onSecondaryContainer),
    tertiary = Color(tertiary), onTertiary = Color(onTertiary),
    tertiaryContainer = Color(tertiaryContainer), onTertiaryContainer = Color(onTertiaryContainer),
    error = Color(error), onError = Color(onError),
    errorContainer = Color(errorContainer), onErrorContainer = Color(onErrorContainer),
    background = Color(background), onBackground = Color(onBackground),
    surface = Color(surface), onSurface = Color(onSurface),
    surfaceVariant = Color(surfaceVariant), onSurfaceVariant = Color(onSurfaceVariant),
    surfaceDim = Color(surfaceDim), surfaceBright = Color(surfaceBright),
    surfaceContainerLowest = Color(surfaceContainerLowest), surfaceContainerLow = Color(surfaceContainerLow),
    surfaceContainer = Color(surfaceContainer), surfaceContainerHigh = Color(surfaceContainerHigh),
    surfaceContainerHighest = Color(surfaceContainerHighest),
    outline = Color(outline), outlineVariant = Color(outlineVariant),
    inverseSurface = Color(inverseSurface), inverseOnSurface = Color(inverseOnSurface),
    inversePrimary = Color(inversePrimary),
    // Flat cards: elevation is the tonal surfaceContainer ramp, never a blue wash.
    surfaceTint = Color.Transparent,
)

/** WCAG 2.x relative luminance and contrast ratio. */
object ColorMath {
    fun contrastRatio(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(argb: Long): Double {
        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun channel(value: Long): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
}
