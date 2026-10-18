package com.tagforge.app.feature.productivity

import com.tagforge.app.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** PR-03: launcher shortcuts and widget buttons are links that map to exactly one screen, and nothing else does. */
class ShortcutIntentTest {

    @Test
    fun everyTargetSurvivesTheRoundTripThroughItsLink() {
        val targets = listOf(ShortcutTarget.Scan, ShortcutTarget.Write, ShortcutTarget.WriteTemplate(42), ShortcutTarget.WriteTemplate(7))
        for (target in targets) {
            assertEquals(target, ShortcutLinks.parse(ShortcutLinks.uri(target)), "round trip of $target")
        }
    }

    @Test
    fun linksHaveTheDocumentedShape() {
        assertEquals("tagforge://scan", ShortcutLinks.uri(ShortcutTarget.Scan))
        assertEquals("tagforge://write", ShortcutLinks.uri(ShortcutTarget.Write))
        assertEquals("tagforge://write/template/42", ShortcutLinks.uri(ShortcutTarget.WriteTemplate(42)))
    }

    @Test
    fun targetsMapToTheirRoutesAndATemplateShortcutArmsTheWrite() {
        assertEquals(Route.Read, ShortcutLinks.route(ShortcutTarget.Scan))
        assertEquals(Route.Write(), ShortcutLinks.route(ShortcutTarget.Write))
        assertEquals(Route.Write(templateId = 42, armOnOpen = true), ShortcutLinks.route(ShortcutTarget.WriteTemplate(42)))
    }

    @Test
    fun foreignMalformedOrMissingLinksAreIgnored() {
        assertNull(ShortcutLinks.parse(null))
        assertNull(ShortcutLinks.parse(""))
        assertNull(ShortcutLinks.parse("https://example.com/write"))
        assertNull(ShortcutLinks.parse("tagforge://erase"))
        assertNull(ShortcutLinks.parse("tagforge://write/template/abc"))
        assertNull(ShortcutLinks.parse("tagforge://write/template/-5"))
        assertNull(ShortcutLinks.parse("tagforge://write/template/"))
        assertNull(ShortcutLinks.parse("tagforge://write/template/42/extra"))
    }
}
