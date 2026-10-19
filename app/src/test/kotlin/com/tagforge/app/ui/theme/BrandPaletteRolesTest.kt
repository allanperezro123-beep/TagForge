package com.tagforge.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/** Branding: the role table of branding/palette.md (section 2) is the single source; the code must not drift from it. */
class BrandPaletteRolesTest {

    /** name, light hex, dark hex: copied from palette.md. */
    private val table = """
        primary 1D55D0 6F9EFE
        onPrimary FFFFFF 001454
        primaryContainer D5E4FE 234387
        onPrimaryContainer 001454 D5E4FE
        secondary 526072 B8C7DD
        onSecondary FFFFFF 253142
        secondaryContainer D4E4FA 3A4859
        onSecondaryContainer 101C2C D4E4FA
        tertiary 027080 58D6EE
        onTertiary FFFFFF 00363E
        tertiaryContainer B6F2FE 004E5A
        onTertiaryContainer 011F25 9FEEFF
        error B3261E F2B8B5
        onError FFFFFF 601410
        errorContainer F9DEDC 8C1D18
        onErrorContainer 410E0B F9DEDC
        background F6F9FF 0E1622
        onBackground 181B20 DEE2E9
        surface F6F9FF 0E1622
        onSurface 181B20 DEE2E9
        surfaceVariant DCE3EF 3F4854
        onSurfaceVariant 414851 BFC7D3
        outline 717882 8A929C
        outlineVariant BFC7D3 3F4854
        surfaceContainerLowest FFFFFF 080E19
        surfaceContainerLow EFF4FA 141B26
        surfaceContainer EAEEF5 19202B
        surfaceContainerHigh E4E9EF 212934
        surfaceContainerHighest DEE2E9 29303C
        inverseSurface 2D3135 DEE2E9
        inverseOnSurface EDF1F7 2D3135
        inversePrimary AAC8FF 1D55D0
    """.trimIndent().lines().map { it.trim().split(" ") }

    private fun roles(p: PaletteSpec): Map<String, Long> = mapOf(
        "primary" to p.primary, "onPrimary" to p.onPrimary,
        "primaryContainer" to p.primaryContainer, "onPrimaryContainer" to p.onPrimaryContainer,
        "secondary" to p.secondary, "onSecondary" to p.onSecondary,
        "secondaryContainer" to p.secondaryContainer, "onSecondaryContainer" to p.onSecondaryContainer,
        "tertiary" to p.tertiary, "onTertiary" to p.onTertiary,
        "tertiaryContainer" to p.tertiaryContainer, "onTertiaryContainer" to p.onTertiaryContainer,
        "error" to p.error, "onError" to p.onError,
        "errorContainer" to p.errorContainer, "onErrorContainer" to p.onErrorContainer,
        "background" to p.background, "onBackground" to p.onBackground,
        "surface" to p.surface, "onSurface" to p.onSurface,
        "surfaceVariant" to p.surfaceVariant, "onSurfaceVariant" to p.onSurfaceVariant,
        "outline" to p.outline, "outlineVariant" to p.outlineVariant,
        "surfaceContainerLowest" to p.surfaceContainerLowest, "surfaceContainerLow" to p.surfaceContainerLow,
        "surfaceContainer" to p.surfaceContainer, "surfaceContainerHigh" to p.surfaceContainerHigh,
        "surfaceContainerHighest" to p.surfaceContainerHighest,
        "inverseSurface" to p.inverseSurface, "inverseOnSurface" to p.inverseOnSurface,
        "inversePrimary" to p.inversePrimary,
    )

    private fun expected(column: Int): Map<String, Long> =
        table.associate { it[0] to (0xFF000000L or it[column].toLong(16)) }

    @Test
    fun lightRolesMatchThePaletteTable() {
        assertEquals(32, table.size)
        assertEquals(expected(1), roles(ForgePalette.light))
    }

    @Test
    fun darkRolesMatchThePaletteTable() {
        assertEquals(expected(2), roles(ForgePalette.dark))
    }

    @Test
    fun surfacesStayFlatBecauseTheTintIsTransparent() {
        assertEquals(Color.Transparent, ForgePalette.light.toLightColorScheme().surfaceTint)
        assertEquals(Color.Transparent, ForgePalette.dark.toDarkColorScheme().surfaceTint)
    }

    @Test
    fun theSchemesCarryTheBrandPrimaryAndTertiary() {
        assertEquals(Color(0xFF1D55D0), ForgePalette.light.toLightColorScheme().primary)
        assertEquals(Color(0xFF58D6EE), ForgePalette.dark.toDarkColorScheme().tertiary)
    }
}
