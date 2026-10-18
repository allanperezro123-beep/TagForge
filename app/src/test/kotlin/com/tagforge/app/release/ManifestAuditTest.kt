package com.tagforge.app.release

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RB-04. Part one pins the audit rules against small synthetic manifests (so adding SEND_SMS really fails the audit);
 * part two applies them to the manifest AGP merges for the release build, wired in by Gradle as a system property.
 */
class ManifestAuditTest {

    private fun manifest(
        permissions: List<String> = listOf("android.permission.NFC"),
        application: String = """android:allowBackup="false" android:dataExtractionRules="@xml/rules"""",
        nfcFeature: String = """<uses-feature android:name="android.hardware.nfc" android:required="false" />""",
    ): String = buildString {
        append("""<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.tagforge.app">""")
        permissions.forEach { append("""<uses-permission android:name="$it" />""") }
        append(nfcFeature)
        append("<application $application />")
        append("</manifest>")
    }

    // ---- the rules ---------------------------------------------------------------------------------------------------

    @Test
    fun aCleanManifestPasses() {
        assertEquals(emptyList(), ManifestAuditor.audit(manifest(permissions = ManifestAuditor.ALLOWED_PERMISSIONS.toList())))
    }

    @Test
    fun addingSendSmsFailsTheAudit() {
        val problems = ManifestAuditor.audit(manifest(permissions = listOf("android.permission.NFC", "android.permission.SEND_SMS")))

        assertEquals(listOf("forbidden permission android.permission.SEND_SMS"), problems)
    }

    @Test
    fun everyForbiddenPermissionIsCaught() {
        for (permission in ManifestAuditor.FORBIDDEN_PERMISSIONS) {
            assertTrue(ManifestAuditor.audit(manifest(permissions = listOf(permission))).any { "forbidden" in it }, permission)
        }
    }

    @Test
    fun aPermissionOutsideTheAllowlistFails() {
        val problems = ManifestAuditor.audit(manifest(permissions = listOf("android.permission.READ_CONTACTS")))

        assertEquals(listOf("permission outside the allowlist: android.permission.READ_CONTACTS"), problems)
    }

    @Test
    fun theAppsOwnPermissionNamespaceIsAllowed() {
        assertEquals(emptyList(), ManifestAuditor.audit(manifest(permissions = listOf("com.tagforge.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))))
    }

    @Test
    fun aDebuggableReleaseFails() {
        val xml = manifest(application = """android:allowBackup="false" android:debuggable="true"""")

        assertTrue(ManifestAuditor.audit(xml).contains("application is debuggable"))
    }

    @Test
    fun backupMustBeOffOrLimitedByRules() {
        assertTrue(ManifestAuditor.audit(manifest(application = """android:allowBackup="true"""")).any { "allowBackup" in it })
        assertEquals(emptyList(), ManifestAuditor.audit(manifest(application = """android:allowBackup="true" android:dataExtractionRules="@xml/r"""")))
    }

    @Test
    fun nfcMustBeOptional() {
        val required = manifest(nfcFeature = """<uses-feature android:name="android.hardware.nfc" android:required="true" />""")
        val absent = manifest(nfcFeature = "")

        assertEquals(listOf("nfc feature must be required=false"), ManifestAuditor.audit(required))
        assertEquals(listOf("missing <uses-feature android.hardware.nfc>"), ManifestAuditor.audit(absent))
    }

    // ---- the real merged release manifest -----------------------------------------------------------------------------

    @Test
    fun theMergedReleaseManifestPassesTheAudit() {
        val path = System.getProperty("tagforge.releaseManifest")
        assertTrue(path != null && File(path).isFile, "Gradle must pass the merged release manifest: $path")

        val xml = File(path!!).readText()
        assertEquals(emptyList(), ManifestAuditor.audit(xml), "merged release manifest")
    }

    @Test
    fun theMergedReleaseManifestIsNotDebuggableAndCarriesTheDataExtractionRules() {
        val doc = ManifestAuditor.parse(File(System.getProperty("tagforge.releaseManifest")!!).readText())
        val application = doc.getElementsByTagName("application").item(0) as org.w3c.dom.Element

        assertEquals("", application.getAttributeNS(ManifestAuditor.ANDROID_NS, "debuggable"))
        assertEquals("false", application.getAttributeNS(ManifestAuditor.ANDROID_NS, "allowBackup"))
        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(ManifestAuditor.ANDROID_NS, "dataExtractionRules"))
    }
}
