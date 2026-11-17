package com.tagforge.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WU1b (S4, S6): the app has no Compose/Robolectric unit-test harness, so the security wiring of the screens (touch
 * filtering, FLAG_SECURE, masked secrets) is pinned by reading the sources. This is a regression guard against someone
 * deleting a one-liner, NOT a proof that the window really behaves that way: that needs a device.
 */
class SecurityWiringTest {

    private val root: File = listOf(File("src/main/kotlin/com/tagforge/app"), File("app/src/main/kotlin/com/tagforge/app"))
        .first { it.exists() }

    private fun source(relative: String): String = File(root, relative).readText()

    private fun code(relative: String): String = source(relative).lines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("//") || it.trim().startsWith("/*") }.joinToString("\n")

    // ---- S4 tapjacking ----

    @Test
    fun theExportedTaskRunnerIgnoresTouchesWhileItsWindowIsObscured() {
        assertTrue("rejectObscuredTouches()" in code("feature/taskrunner/TaskRunnerActivity.kt"))
    }

    @Test
    fun theConfirmationSheetAndTheTypedConfirmationDialogFilterTouchesInTheirOwnWindow() {
        // Both are separate windows (ModalBottomSheet / AlertDialog): the Activity's decor view does not cover them.
        assertTrue("RejectObscuredTouches()" in code("feature/taskrunner/TaskConfirmationSheet.kt"))
        assertTrue("RejectObscuredTouches()" in code("ui/components/TypedConfirmationDialog.kt"))
    }

    @Test
    fun theSecurityAndRawConsoleScreensFilterObscuredTouches() {
        assertTrue("RejectObscuredTouches()" in code("feature/tools/security/SecurityRoute.kt"))
        assertTrue("RejectObscuredTouches()" in code("feature/tools/security/RawConsoleRoute.kt"))
    }

    // ---- S6 FLAG_SECURE coverage ----

    @Test
    fun everyScreenThatShowsSecretsOrAFullDumpIsFlagSecure() {
        val routes = listOf(
            "feature/tools/security/SecurityRoute.kt",
            "feature/tools/security/RawConsoleRoute.kt",
            "feature/tools/DumpRoute.kt",
            "feature/tasks/TaskBuilderRoute.kt",
            "feature/productivity/QrRoute.kt",
            "feature/write/WriteRoute.kt",
        )
        val missing = routes.filter { "SecureWindow()" !in code(it) }
        assertEquals(emptyList(), missing, "Routes without SecureWindow()")
    }

    @Test
    fun theWriteWizardIsOnlySecureWhileAWifiRecordIsInIt() {
        // An ordinary text or URL record must not lose screenshots: the flag is conditional on a Wi-Fi spec.
        val write = code("feature/write/WriteRoute.kt")
        assertTrue(Regex("""if \(.*RecordSpec\.Wifi.*\) SecureWindow\(\)""").containsMatchIn(write), "SecureWindow must be conditional on Wi-Fi")
    }

    // ---- S6 masked secrets ----

    @Test
    fun secretsStartHiddenAndNoCallerForcesThemVisible() {
        assertTrue("startVisible: Boolean = false" in code("feature/tools/security/SecretField.kt"))
        val offenders = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { "startVisible = true" in it.readText() }
            .map { it.name }.toList()
        assertEquals(emptyList(), offenders, "Files that force a secret to start visible")
    }

    @Test
    fun theWifiKeyFieldsOfTheTaskBuilderAndTheQrCreatorAreMasked() {
        assertTrue("secret = true" in code("feature/tasks/forms/LinkForms.kt"))
        assertTrue("secret = true" in code("feature/productivity/QrCreateSection.kt"))
    }

    // ---- S9 export cache ----

    @Test
    fun theApplicationSweepsStaleExportsAtStartup() {
        assertTrue("ExportFiles.sweepQuietly" in code("TagForgeApplication.kt"))
    }

    // ---- WU3 session credentials wiring ----

    @Test
    fun sessionCredentialsAreWiredAcrossContainerSecurityAndRawNfcChannel() {
        val container = code("AppContainer.kt")
        assertTrue("SessionCredentials(clock)" in container, "AppContainer must create one process-wide SessionCredentials")
        assertTrue("credentials = sessionCredentials" in container, "AppContainer must pass sessionCredentials to ScanCoordinator")
        assertTrue("credentials = it.sessionCredentials" in code("feature/tools/security/SecurityRoute.kt"))
        assertTrue("override val isConnected: Boolean" in code("infrastructure/nfc/AndroidTransceiver.kt"))
        assertTrue("forgetAll()" in code("TagForgeApplication.kt"))
    }
}
