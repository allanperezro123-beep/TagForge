package com.tagforge.app.feature.more

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ownership: the About screen names Blue Code as the developer and rights holder, in both languages. */
class AboutStringsTest {

    private fun stringsOf(dir: String): Map<String, String> {
        val file = listOf(File("src/main/res/$dir/strings.xml"), File("app/src/main/res/$dir/strings.xml")).first { it.isFile }
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i) as org.w3c.dom.Element
            node.getAttribute("name") to node.textContent
        }
    }

    @Test
    fun englishNamesBlueCodeAsDeveloperAndOwner() {
        val en = stringsOf("values")
        assertEquals("Developed by Blue Code", en["about_developer"])
        assertEquals("\u00A9 2026 Blue Code. All rights reserved.", en["about_copyright"])
    }

    @Test
    fun spanishNamesBlueCodeAsDeveloperAndOwner() {
        val es = stringsOf("values-es")
        assertEquals("Desarrollado por Blue Code", es["about_developer"])
        assertEquals("\u00A9 2026 Blue Code. Todos los derechos reservados.", es["about_copyright"])
    }

    @Test
    fun theApplicationIdStaysTheSameSoExistingInstallsKeepWorking() {
        val script = listOf(File("build.gradle.kts"), File("app/build.gradle.kts")).first { it.isFile }.readText()
        assertTrue(Regex("""applicationId\s*=\s*"com\.tagforge\.app"""").containsMatchIn(script))
    }
}
