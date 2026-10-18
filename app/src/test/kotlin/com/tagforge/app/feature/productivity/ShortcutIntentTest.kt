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
    fun targetsMapToTheirRoutesAndAnUnvouchedTemplateLinkNeverArmsTheWrite() {
        assertEquals(Route.Read, ShortcutLinks.route(ShortcutTarget.Scan))
        assertEquals(Route.Write(), ShortcutLinks.route(ShortcutTarget.Write))
        assertEquals(Route.Write(templateId = 42, armOnOpen = false), ShortcutLinks.route(ShortcutTarget.WriteTemplate(42)))
        assertEquals(Route.Write(templateId = 42, armOnOpen = false), ShortcutLinks.route(ShortcutLinks.parse("tagforge://write/template/42")!!))
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

    // ---- W4: only a link that carries this install's secret nonce may arm a write ------------------------------------------

    private val nonce = "ab".repeat(32)

    @Test
    fun aPinnedTemplateLinkCarryingTheInstallNonceArmsTheWrite() {
        val link = ShortcutLinks.uri(ShortcutTarget.WriteTemplate(42), nonce)

        assertEquals("tagforge://write/template/42?n=$nonce", link)
        val target = ShortcutLinks.resolve(link, expectedNonce = nonce)
        assertEquals(ShortcutTarget.WriteTemplate(42, armOnOpen = true), target)
        assertEquals(Route.Write(templateId = 42, armOnOpen = true), ShortcutLinks.route(target!!))
    }

    @Test
    fun aTemplateLinkWithAWrongMissingOrUnknownNonceOpensTheReviewWithoutArming() {
        val unarmed = ShortcutTarget.WriteTemplate(42, armOnOpen = false)

        assertEquals(unarmed, ShortcutLinks.resolve("tagforge://write/template/42", expectedNonce = nonce), "no nonce")
        assertEquals(unarmed, ShortcutLinks.resolve("tagforge://write/template/42?n=", expectedNonce = nonce), "empty nonce")
        assertEquals(unarmed, ShortcutLinks.resolve("tagforge://write/template/42?n=${"cd".repeat(32)}", expectedNonce = nonce), "wrong nonce")
        assertEquals(unarmed, ShortcutLinks.resolve("tagforge://write/template/42?n=$nonce", expectedNonce = null), "no nonce issued yet")
        assertEquals(unarmed, ShortcutLinks.resolve("tagforge://write/template/42?x=$nonce", expectedNonce = nonce), "wrong parameter")
    }

    @Test
    fun staticAndNonTemplateLinksNeverArmWhateverTheyCarry() {
        assertEquals(ShortcutTarget.Scan, ShortcutLinks.resolve("tagforge://scan?n=$nonce", expectedNonce = nonce))
        assertEquals(ShortcutTarget.Write, ShortcutLinks.resolve("tagforge://write?n=$nonce", expectedNonce = nonce))
        assertEquals(Route.Write(), ShortcutLinks.route(ShortcutLinks.resolve("tagforge://write?n=$nonce", expectedNonce = nonce)!!))
        assertNull(ShortcutLinks.resolve("https://example.com/write/template/42?n=$nonce", expectedNonce = nonce))
        assertNull(ShortcutLinks.resolve("tagforge://write/template/abc?n=$nonce", expectedNonce = nonce))
    }
}
