package com.tagforge.app.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** PR-04: the home-screen widget sits outside the Compose theme, so its own colours are checked here (WCAG AA, 4.5:1). */
class WidgetContrastTest {

    private fun colors(path: String): Map<String, Long> {
        val xml = listOf(File("src/main/res/$path"), File("app/src/main/res/$path")).first { it.exists() }.readText()
        return Regex("""<color name="([^"]+)">#([0-9A-Fa-f]{6})</color>""").findAll(xml)
            .associate { it.groupValues[1] to (0xFF000000L or it.groupValues[2].toLong(16)) }
    }

    private fun ratio(colors: Map<String, Long>): Double {
        val background = assertNotNullColor(colors, "widget_background")
        val text = assertNotNullColor(colors, "widget_text")
        return ColorMath.contrastRatio(background, text)
    }

    private fun assertNotNullColor(colors: Map<String, Long>, name: String): Long {
        assertTrue(name in colors, "$name is defined")
        return colors.getValue(name)
    }

    @Test
    fun lightWidgetTextMeetsAa() {
        val ratio = ratio(colors("values/colors.xml"))
        assertTrue(ratio >= 4.5, "light widget contrast was $ratio")
    }

    @Test
    fun darkWidgetTextMeetsAa() {
        val ratio = ratio(colors("values-night/colors.xml"))
        assertTrue(ratio >= 4.5, "dark widget contrast was $ratio")
    }

    @Test
    fun theTwoModesUseDifferentSurfaces() {
        val light = colors("values/colors.xml").getValue("widget_background")
        val dark = colors("values-night/colors.xml").getValue("widget_background")
        assertEquals(false, light == dark)
    }
}
