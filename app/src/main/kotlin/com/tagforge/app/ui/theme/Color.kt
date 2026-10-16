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
 * The "forge" palette: mineral, no pure black or white. Used below API 31 and whenever dynamic colour is off.
 * Every text pair is checked at 4.5:1 by ContrastTest.
 */
object ForgePalette {
    val light = PaletteSpec(
        primary = 0xFF9A4426, onPrimary = 0xFFFBFBFA, primaryContainer = 0xFFFFDBCE, onPrimaryContainer = 0xFF3A0F02,
        secondary = 0xFF4A5859, onSecondary = 0xFFFBFBFA, secondaryContainer = 0xFFD9E4E5, onSecondaryContainer = 0xFF0F1D1E,
        tertiary = 0xFF4E6150, onTertiary = 0xFFFBFBFA, tertiaryContainer = 0xFFD0E8D0, onTertiaryContainer = 0xFF0B200E,
        error = 0xFFB3261E, onError = 0xFFFBFBFA, errorContainer = 0xFFF9DEDC, onErrorContainer = 0xFF410E0B,
        background = 0xFFFBFBFA, onBackground = 0xFF1C1B19,
        surface = 0xFFFBFBFA, onSurface = 0xFF1C1B19, surfaceVariant = 0xFFEDE0DA, onSurfaceVariant = 0xFF55433C,
        surfaceDim = 0xFFDCDCD9, surfaceBright = 0xFFFBFBFA,
        surfaceContainerLowest = 0xFFFDFDFC, surfaceContainerLow = 0xFFF8F8F6, surfaceContainer = 0xFFF5F5F3,
        surfaceContainerHigh = 0xFFEFEFEC, surfaceContainerHighest = 0xFFE9E9E6,
        outline = 0xFF7A685F, outlineVariant = 0xFFD8C2BA,
        inverseSurface = 0xFF302F2D, inverseOnSurface = 0xFFF3F1EE, inversePrimary = 0xFFFFB59A,
        success = 0xFF2E6B3A, warning = 0xFF7A5A00,
    )

    val dark = PaletteSpec(
        primary = 0xFFFFB59A, onPrimary = 0xFF5A1B05, primaryContainer = 0xFF7A2E14, onPrimaryContainer = 0xFFFFDBCE,
        secondary = 0xFFB1C3C4, onSecondary = 0xFF1C2B2C, secondaryContainer = 0xFF334243, onSecondaryContainer = 0xFFCDE0E0,
        tertiary = 0xFFB5CCB5, onTertiary = 0xFF213523, tertiaryContainer = 0xFF364B38, onTertiaryContainer = 0xFFD1E8D1,
        error = 0xFFF2B8B5, onError = 0xFF601410, errorContainer = 0xFF8C1D18, onErrorContainer = 0xFFF9DEDC,
        background = 0xFF121211, onBackground = 0xFFE8E6E3,
        surface = 0xFF121211, onSurface = 0xFFE8E6E3, surfaceVariant = 0xFF53433C, onSurfaceVariant = 0xFFD8C2BA,
        surfaceDim = 0xFF121211, surfaceBright = 0xFF383836,
        surfaceContainerLowest = 0xFF0E0E0D, surfaceContainerLow = 0xFF141413, surfaceContainer = 0xFF161615,
        surfaceContainerHigh = 0xFF1F1F1D, surfaceContainerHighest = 0xFF292927,
        outline = 0xFFA08D85, outlineVariant = 0xFF53433C,
        inverseSurface = 0xFFE8E6E3, inverseOnSurface = 0xFF30302E, inversePrimary = 0xFF9A4426,
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
