package com.tagforge.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TK-09 and the Phase 4 slice of RB-04: the manifest asks only for what the spec allows and never for the three
 * permissions the privacy promise rules out. (The full merged-release audit arrives in Phase 6.)
 */
class ManifestPermissionsTest {

    private val manifest: String = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
        .first { it.exists() }
        .readText()

    private val requested: Set<String> = Regex("""<uses-permission(?:-sdk-23)?\s+[^>]*?android:name="([^"]+)"""")
        .findAll(manifest)
        .map { it.groupValues[1] }
        .toSet()

    /** RB-04 allowlist; Phase 4 uses the subset below it. */
    private val allowlist = setOf(
        "android.permission.NFC",
        "android.permission.VIBRATE",
        "android.permission.INTERNET",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.WRITE_SETTINGS",
        "com.android.alarm.permission.SET_ALARM",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.CAMERA",
        "android.permission.ACCESS_NOTIFICATION_POLICY",
        "android.permission.CHANGE_WIFI_STATE",
    )

    @Test
    fun phaseFourRequestsExactlyTheDocumentedPermissions() {
        assertEquals(
            setOf(
                "android.permission.NFC",
                "android.permission.VIBRATE",
                "android.permission.INTERNET",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.WRITE_SETTINGS",
                "android.permission.ACCESS_NOTIFICATION_POLICY",
                "com.android.alarm.permission.SET_ALARM",
                "android.permission.CHANGE_WIFI_STATE",
            ),
            requested,
        )
    }

    @Test
    fun everyRequestedPermissionIsInsideTheSpecAllowlist() {
        assertTrue(allowlist.containsAll(requested), "outside the allowlist: ${requested - allowlist}")
    }

    @Test
    fun theThreeForbiddenPermissionsAreNeverRequested() {
        listOf("QUERY_ALL_PACKAGES", "SEND_SMS", "CALL_PHONE").forEach { name ->
            assertFalse(requested.any { it.endsWith(".$name") }, "$name must never be requested")
        }
    }

    @Test
    fun theProtectedWriteSettingsPermissionIsAnnotatedForLint() {
        assertTrue(
            Regex("""<uses-permission[^>]*WRITE_SETTINGS[^>]*tools:ignore="ProtectedPermissions"""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(manifest),
            "WRITE_SETTINGS is granted specially by the user",
        )
    }

    @Test
    fun legacyWifiPermissionStopsWhereTheSuggestionApiEnds() {
        assertTrue(
            Regex("""CHANGE_WIFI_STATE"[^>]*android:maxSdkVersion="29"""").containsMatchIn(manifest),
            "CHANGE_WIFI_STATE is only needed for the legacy config (<= 28) and network suggestions (29)",
        )
    }

    @Test
    fun launcherAndTextToSpeechQueriesAreDeclaredInsteadOfBroadPackageVisibility() {
        assertTrue("<queries>" in manifest)
        assertTrue("android.intent.action.MAIN" in manifest.substringAfter("<queries>").substringBefore("</queries>"))
        assertTrue("android.intent.category.LAUNCHER" in manifest.substringAfter("<queries>").substringBefore("</queries>"))
        assertTrue("android.intent.action.TTS_SERVICE" in manifest.substringAfter("<queries>").substringBefore("</queries>"))
    }
}
