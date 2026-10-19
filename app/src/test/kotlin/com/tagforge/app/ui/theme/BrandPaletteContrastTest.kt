package com.tagforge.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Branding: the pairs of branding/palette.md (sections 3 and 4) measured on the real palette objects.
 * Body text needs 4.5:1 (WCAG AA), borders and icons 3:1, logo gradient stops 3:1 on their surface.
 */
class BrandPaletteContrastTest {

    private class Check(val name: String, val fg: (PaletteSpec) -> Long, val bg: (PaletteSpec) -> Long, val min: Double)

    private val checks = listOf(
        Check("onSurface on surface", { it.onSurface }, { it.surface }, TEXT),
        Check("onSurfaceVariant on surface", { it.onSurfaceVariant }, { it.surface }, TEXT),
        Check("onSurface on card", { it.onSurface }, { it.surfaceContainerHigh }, TEXT),
        Check("onSurfaceVariant on card", { it.onSurfaceVariant }, { it.surfaceContainerHigh }, TEXT),
        Check("primary on surface", { it.primary }, { it.surface }, TEXT),
        Check("primary on card", { it.primary }, { it.surfaceContainerHigh }, TEXT),
        Check("onPrimary on primary", { it.onPrimary }, { it.primary }, TEXT),
        Check("onPrimaryContainer on primaryContainer", { it.onPrimaryContainer }, { it.primaryContainer }, TEXT),
        Check("tertiary on surface (Forge wordmark)", { it.tertiary }, { it.surface }, TEXT),
        Check("tertiary on card", { it.tertiary }, { it.surfaceContainerHigh }, TEXT),
        Check("onTertiary on tertiary", { it.onTertiary }, { it.tertiary }, TEXT),
        Check("onTertiaryContainer on tertiaryContainer", { it.onTertiaryContainer }, { it.tertiaryContainer }, TEXT),
        Check("onSecondaryContainer on secondaryContainer", { it.onSecondaryContainer }, { it.secondaryContainer }, TEXT),
        Check("error on surface", { it.error }, { it.surface }, TEXT),
        Check("onError on error", { it.onError }, { it.error }, TEXT),
        Check("onErrorContainer on errorContainer", { it.onErrorContainer }, { it.errorContainer }, TEXT),
        Check("outline on surface", { it.outline }, { it.surface }, UI),
        Check("primary on surface (icons, focus ring)", { it.primary }, { it.surface }, UI),
        Check("tertiary on surface (icons)", { it.tertiary }, { it.surface }, UI),
    )

    private fun failures(palette: PaletteSpec) = checks
        .map { it to ColorMath.contrastRatio(it.bg(palette), it.fg(palette)) }
        .filter { (check, ratio) -> ratio < check.min }
        .map { (check, ratio) -> "${check.name}=%.2f (needs ${check.min})".format(ratio) }

    @Test
    fun everyLightPairMeetsItsThreshold() {
        assertEquals(19, checks.size, "the pair list mirrors palette.md section 3")
        assertEquals(emptyList(), failures(ForgePalette.light))
    }

    @Test
    fun everyDarkPairMeetsItsThreshold() {
        assertEquals(emptyList(), failures(ForgePalette.dark))
    }

    @Test
    fun measuredRatiosMatchTheDocumentedTables() {
        val light = ForgePalette.light
        val dark = ForgePalette.dark
        assertEquals(16.37, ColorMath.contrastRatio(light.surface, light.onSurface), 0.02)
        assertEquals(6.10, ColorMath.contrastRatio(light.surface, light.primary), 0.02)
        assertEquals(5.48, ColorMath.contrastRatio(light.surface, light.tertiary), 0.02)
        assertEquals(4.23, ColorMath.contrastRatio(light.surface, light.outline), 0.02)
        assertEquals(13.97, ColorMath.contrastRatio(dark.surface, dark.onSurface), 0.02)
        assertEquals(10.61, ColorMath.contrastRatio(dark.surface, dark.tertiary), 0.02)
        assertEquals(5.77, ColorMath.contrastRatio(dark.surface, dark.outline), 0.02)
    }

    @Test
    fun logoGradientStopsHoldThreeToOneOnTheirSurface() {
        val logo = listOf(
            Triple("blue on launcher navy", 0xFF3670EE, NAVY),
            Triple("cyan on launcher navy", 0xFF48D2E9, NAVY),
            Triple("light blue on light surface", 0xFF275FDC, ForgePalette.light.surface),
            Triple("light cyan on light surface", 0xFF0796AB, ForgePalette.light.surface),
        )
        val below = logo.filter { (_, fg, bg) -> ColorMath.contrastRatio(bg, fg) < UI }.map { it.first }
        assertEquals(emptyList(), below)
        assertTrue(ColorMath.contrastRatio(NAVY, 0xFF3670EE) in 4.0..4.2, "blue stop is the weakest dark pair (4.07)")
    }

    @Test
    fun theDarkSurfaceIsTheLauncherNavy() {
        assertEquals(NAVY, ForgePalette.dark.surface)
        assertEquals(NAVY, ForgePalette.dark.background)
    }

    private companion object {
        const val TEXT = 4.5
        const val UI = 3.0
        const val NAVY = 0xFF0E1622L
    }
}
