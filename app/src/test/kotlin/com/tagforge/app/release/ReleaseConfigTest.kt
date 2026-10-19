package com.tagforge.app.release

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RB-01 to RB-03 as checks on the files that define the release: R8 on, signing only from the git-ignored
 * keystore.properties (and failing loudly without it), 1.1.0 / 610, and no secret anywhere in the repository.
 */
class ReleaseConfigTest {

    private fun repoFile(path: String): File =
        listOf(File("../$path"), File(path)).firstOrNull { it.exists() } ?: error("Cannot find $path")

    private val buildScript: String get() = repoFile("app/build.gradle.kts").readText()
    private val rules: String get() = repoFile("app/proguard-rules.pro").readText()

    // ---- RB-03 versioning ------------------------------------------------------------------------------------------

    @Test
    fun theReleaseIsOnePointOnePointZeroWithVersionCodeSixHundredTen() {
        assertTrue(Regex("""versionName\s*=\s*"1\.1\.0"""").containsMatchIn(buildScript), "versionName 1.1.0")
        val code = Regex("""versionCode\s*=\s*(\d+)""").find(buildScript)?.groupValues?.get(1)?.toInt()
        assertEquals(610, code, "versionCode = phase * 100 + patch")
    }

    @Test
    fun versionCodeKeepsGrowingFromEveryEarlierPhase() {
        val earlier = listOf(100, 200, 300, 301, 400, 500, 600, 601)
        val code = Regex("""versionCode\s*=\s*(\d+)""").find(buildScript)!!.groupValues[1].toInt()
        assertTrue(earlier.all { it < code }, "installs over every earlier build without uninstalling")
        assertEquals(earlier.sorted(), earlier)
    }

    // ---- RB-01 R8 ---------------------------------------------------------------------------------------------------

    @Test
    fun theReleaseBuildShrinksAndObfuscatesWithOurRules() {
        val release = buildScript.substringAfter("release {").substringBefore("compileOptions")
        assertTrue("isMinifyEnabled = true" in release)
        assertTrue("isShrinkResources = true" in release)
        assertTrue("proguard-rules.pro" in release)
    }

    @Test
    fun theKeepRulesCoverSerializationRoutesAndTheRoomEntities() {
        assertTrue("-keepattributes" in rules && "InnerClasses" in rules)
        assertTrue("\$\$serializer" in rules, "generated serializers of our @Serializable classes")
        assertTrue("kotlinx.serialization.KSerializer serializer(...)" in rules, "Companion.serializer() lookups used by Navigation routes")
        assertTrue("com.tagforge.**" in rules)
    }

    // ---- RB-02 signing ----------------------------------------------------------------------------------------------

    @Test
    fun signingComesOnlyFromTheKeystorePropertiesFileAndNeverFromTheDebugKey() {
        assertTrue("keystore.properties" in buildScript)
        for (key in listOf("storeFile", "storePassword", "keyAlias", "keyPassword")) assertTrue(key in buildScript, key)
        assertFalse("signingConfigs.getByName(\"debug\")" in buildScript, "the release must never be signed with the debug key")
        assertFalse(Regex("""signingConfig\s*=\s*signingConfigs\.(getByName\("debug"\)|debug)""").containsMatchIn(buildScript))
    }

    @Test
    fun withoutTheFileAssembleReleaseFailsAndNamesIt() {
        assertTrue("GradleException" in buildScript)
        assertTrue(Regex("""assembleRelease|packageRelease""").containsMatchIn(buildScript))
        assertTrue("keystore.properties" in buildScript.substringAfter("GradleException"), "the message names the missing file")
    }

    @Test
    fun noSecretsAreCommitted() {
        val ignore = repoFile(".gitignore").readText()
        for (pattern in listOf("keystore.properties", "*.jks", "*.keystore", "*.p12", "*.pfx", "keystore/")) assertTrue(pattern in ignore, pattern)
    }

    @Test
    fun theExampleFileHasOnlyPlaceholders() {
        val example = repoFile("keystore.properties.example").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        val keys = example.associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }

        assertEquals(setOf("storeFile", "storePassword", "keyAlias", "keyPassword"), keys.keys)
        assertEquals("tagforge", keys["keyAlias"])
        assertTrue(keys.getValue("storePassword").startsWith("CHANGE_ME"), "no real password")
        assertTrue(keys.getValue("keyPassword").startsWith("CHANGE_ME"), "no real password")
        assertNotNull(keys["storeFile"])
    }

    @Test
    fun theReleaseGuideIsInSpanishAndExplainsHowToCreateTheKeystore() {
        val guide = repoFile("dist/RELEASE.md").readText()
        assertTrue("keytool -genkeypair" in guide)
        assertTrue("keystore.properties" in guide)
        assertTrue("apksigner verify" in guide)
        assertTrue("contraseña" in guide.lowercase(), "written in Spanish")
    }
}
