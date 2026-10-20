package com.tagforge.app.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UX-B B1: every screen below a bottom-bar destination shows the shared top-left back arrow. The check reads the sources
 * (like the manifest tests do), so a new screen that forgets the arrow fails here instead of on a user's phone.
 */
class BackArrowAuditTest {

    private val root: File = listOf(File("src/main/kotlin/com/tagforge/app"), File("app/src/main/kotlin/com/tagforge/app"))
        .first { it.isDirectory }

    /** The files whose top bar belongs to a bottom-bar destination: they are the roots, there is nothing "before" them. */
    private val topLevelScreens = setOf("ReadScreen.kt", "MoreScreen.kt", "TaskListScreen.kt", "ToolsScreen.kt")

    private val screensWithATopBar: List<File> = root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" && "TopAppBar(" in it.readText() }
        .toList()

    @Test
    fun everyTopBarBelowABottomBarDestinationHasTheSharedBackArrow() {
        val missing = screensWithATopBar
            .filter { it.name !in topLevelScreens }
            .filterNot { "BackNavigationIcon(" in it.readText() }
            .map { it.name }

        assertEquals(emptyList(), missing, "these screens have a top bar without BackNavigationIcon")
    }

    @Test
    fun theWriteWizardIsNotATopLevelScreenSoItAlsoHasTheArrow() {
        assertTrue(screensWithATopBar.any { it.name == "WizardScreen.kt" })
        assertTrue("WizardScreen.kt" !in topLevelScreens)
    }

    @Test
    fun noScreenBuildsItsOwnArrowInsteadOfTheSharedComponent() {
        val handRolled = screensWithATopBar
            .filter { "Icons.AutoMirrored.Outlined.ArrowBack" in it.readText() && it.name != "BackNavigationIcon.kt" }
            .map { it.name }

        assertEquals(emptyList(), handRolled, "use BackNavigationIcon so the arrow, size and description stay consistent")
    }
}
