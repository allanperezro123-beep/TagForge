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

    /** The elements this app declares, minus the ones that only exist to strip a permission a library would add. */
    private val declared: List<String> = Regex("""<uses-permission(?:-sdk-23)?\s[^>]*>""").findAll(manifest).map { it.value }.toList()

    private val requested: Set<String> = declared
        .filterNot { """tools:node="remove"""" in it }
        .mapNotNull { Regex("""android:name="([^"]+)"""").find(it)?.groupValues?.get(1) }
        .toSet()

    @Test
    fun aPermissionACameraXDependencyWouldAddIsStrippedBecauseNothingHereUsesIt() {
        assertTrue(
            declared.any { "android.permission.ACCESS_NETWORK_STATE" in it && """tools:node="remove"""" in it },
            "media3-common (via CameraX) adds ACCESS_NETWORK_STATE; the merged manifest must not carry it",
        )
    }

    /** RB-04 allowlist; Phase 4 uses the subset below it. */
    private val allowlist = setOf(
        "android.permission.NFC",
        "android.permission.VIBRATE",
        "android.permission.INTERNET",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.WRITE_SETTINGS",
        "com.android.alarm.permission.SET_ALARM",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.BLUETOOTH",
        "android.permission.CAMERA",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
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
                // UX-B: all four are requested at runtime, only when the user taps the matching button.
                "android.permission.CAMERA",
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.BLUETOOTH",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
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
        listOf(
            "QUERY_ALL_PACKAGES", "SEND_SMS", "CALL_PHONE", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE",
            "ACCESS_BACKGROUND_LOCATION", "READ_CONTACTS", "RECORD_AUDIO",
        ).forEach { name ->
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

    @Test
    fun legacyBluetoothPermissionStopsWhereTheConnectPermissionBegins() {
        assertTrue(
            Regex("""android\.permission\.BLUETOOTH"[^>]*android:maxSdkVersion="30"""").containsMatchIn(manifest),
            "BLUETOOTH is only needed up to Android 11; Android 12+ uses BLUETOOTH_CONNECT",
        )
    }

    @Test
    fun cameraBluetoothAndLocationHardwareAreOptional() {
        val features = Regex("""<uses-feature\s+([^>]*?)/>""", RegexOption.DOT_MATCHES_ALL).findAll(manifest).map { it.groupValues[1] }.toList()
        listOf("android.hardware.camera", "android.hardware.bluetooth", "android.hardware.location").forEach { name ->
            val feature = features.firstOrNull { """android:name="$name"""" in it }
            assertTrue(feature != null && """android:required="false"""" in feature, "$name must be declared required=false")
        }
    }

    @Test
    fun noPermissionIsDeclaredForBackgroundLocation() {
        assertFalse("ACCESS_BACKGROUND_LOCATION" in manifest.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), ""))
    }

    /** The share target is the one public entry that carries user content, so it is pinned: plain text only, no write is armed. */
    @Test
    fun mainActivityReceivesPlainTextSharesAndNothingElse() {
        val main = manifest.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            .substringAfter("""android:name=".MainActivity"""").substringBefore("</activity>")
        assertTrue("android.intent.action.SEND" in main)
        assertTrue("""<data android:mimeType="text/plain" />""" in main)
        assertTrue("android.intent.category.DEFAULT" in main)
        assertFalse("SEND_MULTIPLE" in main, "only a single text is accepted")
        assertFalse(Regex("""android:mimeType="(?!text/plain)""").containsMatchIn(main), "no other MIME type is accepted")
    }
}
