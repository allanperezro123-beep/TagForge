package com.tagforge.domain.template

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** HT-02 name rules: trimmed, non-blank, unique ignoring case, bounded. */
class TemplateNamesTest {

    @Test
    fun aFreeNameIsKeptTrimmed() {
        assertEquals("WiFi casa", TemplateNames.resolve("  WiFi casa ", existing = listOf("Oficina")))
    }

    @Test
    fun blankNamesAreRejected() {
        assertNull(TemplateNames.resolve("   ", existing = emptyList()))
        assertNull(TemplateNames.resolve("", existing = emptyList()))
    }

    @Test
    fun aCollisionIgnoringCaseGetsTheNextFreeNumber() {
        assertEquals("wifi CASA (2)", TemplateNames.resolve("wifi CASA", existing = listOf("WiFi casa")))
        assertEquals("WiFi casa (3)", TemplateNames.resolve("WiFi casa", existing = listOf("WiFi casa", "WiFi casa (2)")))
    }

    @Test
    fun aTemplateKeepsItsOwnNameWhenRenamedToTheSameThing() {
        // the caller removes the template being renamed from [existing]
        assertEquals("WiFi casa", TemplateNames.resolve("WiFi casa", existing = listOf("Oficina")))
    }

    @Test
    fun longNamesAreCutToTheMaximumBeforeCheckingCollisions() {
        val resolved = TemplateNames.resolve("x".repeat(100), existing = emptyList())!!
        assertEquals(TemplateNames.MAX_LENGTH, resolved.length)
        val second = TemplateNames.resolve("x".repeat(100), existing = listOf(resolved))!!
        assertEquals(true, second.endsWith(" (2)"))
        assertEquals(TemplateNames.MAX_LENGTH, second.length)
    }
}
