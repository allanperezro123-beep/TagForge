package com.tagforge.domain.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/** Branding: the Tag Forge brand palette is the default look; Material You is an opt-in. */
class AppSettingsTest {

    @Test
    fun theBrandPaletteIsTheDefaultLookAndMaterialYouIsOptIn() {
        assertEquals(false, AppSettings().dynamicColor)
    }

    @Test
    fun optingInToDynamicColourIsKeptWhileTheOtherDefaultsStay() {
        val optedIn = AppSettings().copy(dynamicColor = true)

        assertEquals(true, optedIn.dynamicColor)
        assertEquals(ThemeMode.SYSTEM, optedIn.themeMode)
        assertEquals(true, optedIn.hapticsEnabled)
        assertEquals(false, optedIn.advancedMode)
    }
}
