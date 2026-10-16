package com.tagforge.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** AS-04 "Key parity" / rule G1: values/ (en) and values-es/ must define exactly the same keys. */
class StringsParityTest {

    @Test
    fun englishAndSpanishDefineTheSameKeys() {
        val en = keysOf(resFile("values"))
        val es = keysOf(resFile("values-es"))

        assertTrue(en.isNotEmpty(), "English strings must not be empty")
        assertEquals(emptySet(), en - es, "Keys missing in values-es/strings.xml")
        assertEquals(emptySet(), es - en, "Keys missing in values/strings.xml")
    }

    @Test
    fun parserCollectsStringsPluralsAndArraysAndSkipsUntranslatable() {
        val xml = """
            <resources>
                <string name="a">A</string>
                <string name="b" translatable="false">B</string>
                <plurals name="items"><item quantity="one">1</item><item quantity="other">n</item></plurals>
                <string-array name="list"><item>x</item></string-array>
            </resources>
        """.trimIndent()

        assertEquals(setOf("string:a", "plurals:items", "array:list"), parseKeys(xml.byteInputStream()))
    }

    @Test
    fun parserDistinguishesSameNameAcrossKinds() {
        val xml = """<resources><string name="x">1</string><plurals name="x"><item quantity="other">2</item></plurals></resources>"""
        assertEquals(setOf("string:x", "plurals:x"), parseKeys(xml.byteInputStream()))
    }

    private fun keysOf(file: File): Set<String> = file.inputStream().use { parseKeys(it) }

    private fun parseKeys(input: java.io.InputStream): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
        val nodes = doc.documentElement.childNodes
        val keys = linkedSetOf<String>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? org.w3c.dom.Element ?: continue
            if (node.getAttribute("translatable") == "false") continue
            val kind = when (node.tagName) {
                "string" -> "string"
                "plurals" -> "plurals"
                "string-array" -> "array"
                else -> continue
            }
            keys += "$kind:${node.getAttribute("name")}"
        }
        return keys
    }

    private fun resFile(dir: String): File {
        val candidates = listOf(File("src/main/res/$dir/strings.xml"), File("app/src/main/res/$dir/strings.xml"))
        return candidates.firstOrNull { it.isFile } ?: error("Cannot locate $dir/strings.xml from ${File(".").absolutePath}")
    }
}
