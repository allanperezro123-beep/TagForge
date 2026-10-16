package com.tagforge.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** AS-03 / PR-04: every text-on-background pair of the forge palette meets WCAG AA (4.5:1) in light and dark. */
class ContrastTest {

    private data class Pair(val name: String, val background: Long, val foreground: Long)

    private fun pairs(p: PaletteSpec) = listOf(
        Pair("primary/onPrimary", p.primary, p.onPrimary),
        Pair("primaryContainer/onPrimaryContainer", p.primaryContainer, p.onPrimaryContainer),
        Pair("secondary/onSecondary", p.secondary, p.onSecondary),
        Pair("secondaryContainer/onSecondaryContainer", p.secondaryContainer, p.onSecondaryContainer),
        Pair("tertiary/onTertiary", p.tertiary, p.onTertiary),
        Pair("tertiaryContainer/onTertiaryContainer", p.tertiaryContainer, p.onTertiaryContainer),
        Pair("error/onError", p.error, p.onError),
        Pair("errorContainer/onErrorContainer", p.errorContainer, p.onErrorContainer),
        Pair("surface/onSurface", p.surface, p.onSurface),
        Pair("surface/onSurfaceVariant", p.surface, p.onSurfaceVariant),
        Pair("surfaceContainer/onSurface", p.surfaceContainer, p.onSurface),
        Pair("surfaceContainer/onSurfaceVariant", p.surfaceContainer, p.onSurfaceVariant),
        Pair("surfaceContainerHighest/onSurface", p.surfaceContainerHighest, p.onSurface),
        Pair("surfaceContainerHighest/onSurfaceVariant", p.surfaceContainerHighest, p.onSurfaceVariant),
        Pair("inverseSurface/inverseOnSurface", p.inverseSurface, p.inverseOnSurface),
        Pair("inverseSurface/inversePrimary", p.inverseSurface, p.inversePrimary),
        Pair("surface/primary (text button)", p.surface, p.primary),
        Pair("surface/error (error text)", p.surface, p.error),
        Pair("surface/outline", p.surface, p.outline),
        Pair("surface/success", p.surface, p.success),
        Pair("surface/warning", p.surface, p.warning),
        Pair("surfaceContainer/success", p.surfaceContainer, p.success),
        Pair("surfaceContainer/warning", p.surfaceContainer, p.warning),
    )

    private fun assertAllPass(label: String, palette: PaletteSpec) {
        val checked = pairs(palette)
        assertTrue(checked.size >= 20, "palette pair list must stay comprehensive")
        val failures = checked.filter { ColorMath.contrastRatio(it.background, it.foreground) < MIN_RATIO }
            .map { "${it.name}=%.2f".format(ColorMath.contrastRatio(it.background, it.foreground)) }
        assertEquals(emptyList(), failures, "$label pairs below $MIN_RATIO:1")
    }

    @Test
    fun lightPaletteMeetsAa() = assertAllPass("light", ForgePalette.light)

    @Test
    fun darkPaletteMeetsAa() = assertAllPass("dark", ForgePalette.dark)

    @Test
    fun contrastMathMatchesKnownReferenceValues() {
        assertEquals(21.0, ColorMath.contrastRatio(0xFF000000, 0xFFFFFFFF), 0.001)
        assertEquals(1.0, ColorMath.contrastRatio(0xFF9A4426, 0xFF9A4426), 0.001)
        // Design table values (spec: primary light 6.3, dark surface text 15.1)
        assertEquals(6.28, ColorMath.contrastRatio(ForgePalette.light.primary, ForgePalette.light.onPrimary), 0.02)
        assertEquals(15.05, ColorMath.contrastRatio(ForgePalette.dark.surface, ForgePalette.dark.onSurface), 0.02)
    }

    @Test
    fun contrastIsSymmetric() {
        assertEquals(
            ColorMath.contrastRatio(0xFF123456, 0xFFEEDDCC),
            ColorMath.contrastRatio(0xFFEEDDCC, 0xFF123456),
            0.0001,
        )
    }

    private companion object {
        const val MIN_RATIO = 4.5
    }
}
