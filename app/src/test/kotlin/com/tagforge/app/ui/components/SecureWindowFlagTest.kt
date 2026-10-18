package com.tagforge.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** W7: screenshots, recents thumbnails and screen recording are blocked while a screen shows a tag password, and only then. */
class SecureWindowFlagTest {

    private class Window(var secure: Boolean = false, val changes: MutableList<Boolean> = mutableListOf()) {
        val flag = SecureWindowFlag(read = { secure }) { on -> secure = on; changes += on }
    }

    @Test
    fun theWindowIsSecureWhileAScreenHoldsItAndNormalAgainWhenItLetsGo() {
        val window = Window()

        val hold = window.flag.acquire()
        assertTrue(window.secure)
        hold.release()

        assertFalse(window.secure)
        assertEquals(listOf(true, false), window.changes)
    }

    @Test
    fun twoHoldersKeepItSecureUntilTheLastOneReleases() {
        val window = Window()
        val first = window.flag.acquire()
        val second = window.flag.acquire()

        first.release()
        assertTrue(window.secure, "the second screen still shows a secret")
        second.release()

        assertFalse(window.secure)
        assertEquals(listOf(true, false), window.changes, "set once, cleared once")
    }

    @Test
    fun releasingTwiceNeverUnderflowsOrClearsAnotherHoldersFlag() {
        val window = Window()
        val first = window.flag.acquire()
        val second = window.flag.acquire()

        first.release()
        first.release()

        assertTrue(window.secure)
        second.release()
        assertFalse(window.secure)
    }

    @Test
    fun aWindowThatWasAlreadySecureIsLeftSecureAfterwards() {
        val window = Window(secure = true)

        window.flag.acquire().release()

        assertTrue(window.secure, "we only undo what we set")
        assertEquals(emptyList(), window.changes)
    }
}
