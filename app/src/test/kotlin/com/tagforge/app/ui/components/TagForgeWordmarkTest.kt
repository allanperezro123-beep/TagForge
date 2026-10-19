package com.tagforge.app.ui.components

import androidx.compose.ui.graphics.Color
import com.tagforge.app.R
import com.tagforge.app.ui.theme.ForgePalette
import kotlin.test.Test
import kotlin.test.assertEquals

/** The logo lock-up: which mark variant a surface gets, and how the two-colour name is built. */
class TagForgeWordmarkTest {

    @Test
    fun theBrandSurfacesGetTheVariantMadeForThem() {
        assertEquals(R.drawable.ic_logo_mark_dark, logoMarkRes(Color(ForgePalette.dark.surface)))
        assertEquals(R.drawable.ic_logo_mark_light, logoMarkRes(Color(ForgePalette.light.surface)))
    }

    @Test
    fun everyStepOfTheContainerRampKeepsItsModesVariant() {
        val lightRamp = ForgePalette.light.run { listOf(surfaceContainerLowest, surfaceContainerHigh, surfaceContainerHighest) }
        val darkRamp = ForgePalette.dark.run { listOf(surfaceContainerLowest, surfaceContainer, surfaceContainerHighest) }

        assertEquals(List(3) { R.drawable.ic_logo_mark_light }, lightRamp.map { logoMarkRes(Color(it)) })
        assertEquals(List(3) { R.drawable.ic_logo_mark_dark }, darkRamp.map { logoMarkRes(Color(it)) })
    }

    @Test
    fun theNameIsTagInOneColourThenForgeInAnother() {
        val tag = Color(0xFF1D55D0)
        val forge = Color(0xFF027080)

        val name = wordmarkName(tag, forge)

        assertEquals("TagForge", name.text)
        assertEquals(listOf(0 to 3, 3 to 8), name.spanStyles.map { it.start to it.end })
        assertEquals(listOf(tag, forge), name.spanStyles.map { it.item.color })
    }

    @Test
    fun theNameFollowsTheThemeColoursItIsGiven() {
        val dark = ForgePalette.dark
        val name = wordmarkName(Color(dark.primary), Color(dark.tertiary))

        assertEquals(listOf(Color(0xFF6F9EFE), Color(0xFF58D6EE)), name.spanStyles.map { it.item.color })
    }
}
