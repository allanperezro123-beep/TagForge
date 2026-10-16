package com.tagforge.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** AS-01 "Domain purity" / rule G2: nothing under domain/src may import android.*. */
class DomainPurityTest {

    @Test
    fun domainSourcesNeverImportAndroid() {
        val root = domainSrcRoot()
        // This file embeds android imports as detector samples, so it is the only file excluded.
        val files = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "DomainPurityTest.kt" }
            .toList()

        // Guard against a vacuous pass: the scan must actually look at Kotlin sources.
        assertTrue(files.size >= 3, "Expected to scan domain sources, found ${files.size} files under $root")

        val violations = files.flatMap { file ->
            androidImports(file.readText()).map { "${file.relativeTo(root)}: $it" }
        }
        assertEquals(emptyList(), violations, "android.* imports are forbidden in :domain")
    }

    @Test
    fun detectorFlagsAndroidImportsAndIgnoresLookAlikes() {
        val source = """
            package demo
            import android.os.Bundle
            import androidx.compose.runtime.Composable
            import  android.nfc.Tag
            import com.tagforge.domain.android.Thing
            // import android.commented.Out
        """.trimIndent()

        assertEquals(listOf("import android.os.Bundle", "import  android.nfc.Tag"), androidImports(source))
        assertEquals(emptyList(), androidImports("import kotlin.collections.List"))
    }

    private fun androidImports(source: String): List<String> =
        source.lineSequence().map { it.trim() }.filter { ANDROID_IMPORT.containsMatchIn(it) }.toList()

    private fun domainSrcRoot(): File {
        // Gradle runs :domain:test with the module directory as working directory; be tolerant of IDE runs.
        val candidates = listOf(File("src"), File("domain/src"))
        return candidates.firstOrNull { File(it, "main").isDirectory }
            ?: error("Cannot locate domain/src from ${File(".").absolutePath}")
    }

    private companion object {
        val ANDROID_IMPORT = Regex("""^import\s+android\.""")
    }
}
