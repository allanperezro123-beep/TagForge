package com.tagforge.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TK-05: the manifest contract of the task runner, pinned so a refactor cannot quietly weaken it. */
class ManifestTaskRunnerTest {

    private val manifest: String = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
        .first { it.exists() }
        .readText()

    /** The `<activity ...>...</activity>` element of the runner, comments removed. */
    private val runner: String = manifest
        .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        .substringAfter("""android:name=".feature.taskrunner.TaskRunnerActivity"""")
        .substringBefore("</activity>")

    @Test
    fun theRunnerListensForTheTaskMimeTypeOnNdefDiscovered() {
        assertTrue("""android.nfc.action.NDEF_DISCOVERED""" in runner)
        assertTrue("""android.intent.category.DEFAULT""" in runner)
        assertTrue("""<data android:mimeType="application/x-tf" />""" in runner)
    }

    @Test
    fun theRunnerIsSingleTopTranslucentAndOutOfRecents() {
        assertTrue("""android:launchMode="singleTop"""" in runner)
        assertTrue("""android:theme="@style/Theme.TagForge.Translucent"""" in runner)
        assertTrue("""android:excludeFromRecents="true"""" in runner)
        assertTrue("""android:taskAffinity=""""" in runner)
        assertTrue("""android:exported="true"""" in runner)
    }

    @Test
    fun theRunnerNeverShowsOverTheLockScreen() {
        assertFalse("showWhenLocked" in runner, "TK-05: no show-when-locked")
        assertFalse("turnScreenOn" in runner)
        assertFalse("showForAllUsers" in runner)
    }

    @Test
    fun theOnlyNdefFilterInTheAppIsTheTaskRunners() {
        assertEquals(1, Regex("android.nfc.action.NDEF_DISCOVERED").findAll(manifest.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")).count())
        assertFalse("android.nfc.action.TECH_DISCOVERED" in manifest, "everything else is handled by reader mode in the foreground")
    }
}
