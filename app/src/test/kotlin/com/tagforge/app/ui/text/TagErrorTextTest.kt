package com.tagforge.app.ui.text

import com.tagforge.app.R
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.tag.TagError
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Rule G1 for errors: the domain returns codes, the UI maps each one to a string that exists in en AND es. */
class TagErrorTextTest {

    private val everyError: List<TagError> = listOf(
        TagError.TagLost,
        TagError.Timeout,
        TagError.Cancelled,
        TagError.NotNdef,
        TagError.ReadOnly,
        TagError.AuthRequired,
        TagError.Unsupported("MIFARE Classic"),
        TagError.Unsupported(TagError.Unsupported.MIFARE_CLASSIC),
        TagError.Unsupported(TagError.Unsupported.USER_PAGES_MODEL),
        TagError.SameTag,
        TagError.CapacityExceeded(required = 147, available = 137),
        TagError.AuthFailed(attemptsLeft = 2),
        TagError.VerifyMismatch(Bytes.of(1), Bytes.of(2)),
        TagError.Nak(5),
        TagError.Malformed("bad"),
        TagError.Io("boom"),
        TagError.Security(com.tagforge.domain.security.SecurityFailure.UidMismatch),
    )

    private fun stringsOf(dir: String): Map<String, String> {
        val file = listOf(File("src/main/res/$dir/strings.xml"), File("app/src/main/res/$dir/strings.xml")).first { it.isFile }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i) as org.w3c.dom.Element
            node.getAttribute("name") to node.textContent
        }
    }

    private fun keyNameOf(resId: Int): String? =
        R.string::class.java.declaredFields.firstOrNull { it.type == Int::class.javaPrimitiveType && it.getInt(null) == resId }?.name

    private fun placeholderCount(text: String): Int =
        Regex("""%(\d+)\$""").findAll(text).map { it.groupValues[1].toInt() }.maxOrNull() ?: 0

    @Test
    fun theListCoversEveryTagErrorVariant() {
        // Fails when someone adds a TagError subtype without extending this test (and the mapping).
        val variants = assertNotNull(TagError::class.java.permittedSubclasses, "TagError must be a sealed type").size
        assertEquals(variants, everyError.map { it::class.java }.distinct().size)
    }

    @Test
    fun unsupportedClassicAndUserPagesGetTheirOwnExplanationWithoutArguments() {
        val classic = TagErrorText.of(TagError.Unsupported(TagError.Unsupported.MIFARE_CLASSIC))
        assertEquals(R.string.error_unsupported_classic, classic.message)
        assertEquals(emptyList<Any>(), classic.args)

        val pages = TagErrorText.of(TagError.Unsupported(TagError.Unsupported.USER_PAGES_MODEL))
        assertEquals(R.string.error_unsupported_pages_model, pages.message)
        assertEquals(emptyList<Any>(), pages.args)

        assertEquals(R.string.error_same_tag, TagErrorText.of(TagError.SameTag).message)
    }

    @Test
    fun everyErrorMapsToAStringThatExistsInEnglishAndSpanish() {
        val en = stringsOf("values")
        val es = stringsOf("values-es")
        for (error in everyError) {
            val text = TagErrorText.of(error)
            val key = keyNameOf(text.message)
            assertNotNull(key, "no R.string for $error")
            assertTrue(key in en, "$key missing in values/")
            assertTrue(key in es, "$key missing in values-es/")
            text.title?.let { title ->
                val titleKey = keyNameOf(title)
                assertNotNull(titleKey, "no R.string title for $error")
                assertTrue(titleKey in en && titleKey in es, "$titleKey missing in a locale")
            }
        }
    }

    @Test
    fun formatArgumentsMatchThePlaceholdersInBothLocales() {
        val en = stringsOf("values")
        val es = stringsOf("values-es")
        for (error in everyError) {
            val text = TagErrorText.of(error)
            val key = keyNameOf(text.message)!!
            assertEquals(text.args.size, placeholderCount(en.getValue(key)), "$key (en) placeholders for $error")
            assertEquals(text.args.size, placeholderCount(es.getValue(key)), "$key (es) placeholders for $error")
        }
    }

    @Test
    fun specificMappingsCarryTheirData() {
        assertEquals(R.string.error_tag_lost_body, TagErrorText.of(TagError.TagLost).message)
        assertEquals(R.string.error_tag_lost_title, TagErrorText.of(TagError.TagLost).title)
        assertEquals(listOf<Any>(147, 137), TagErrorText.of(TagError.CapacityExceeded(147, 137)).args)
        assertEquals(listOf<Any>(5), TagErrorText.of(TagError.Nak(5)).args)
        assertEquals(listOf<Any>("MIFARE Classic"), TagErrorText.of(TagError.Unsupported("MIFARE Classic")).args)
        assertEquals(R.string.error_auth_required, TagErrorText.of(TagError.AuthRequired).message)
    }

    @Test
    fun aCommunicationErrorShowsItsTechnicalDetailSoFieldReportsAreExact() {
        val withDetail = TagErrorText.of(TagError.Io("IOException: Transceive failed"))
        assertEquals(R.string.error_io_detail, withDetail.message)
        assertEquals(listOf<Any>("IOException: Transceive failed"), withDetail.args)

        assertEquals(R.string.error_io, TagErrorText.of(TagError.Io(null)).message)
        assertEquals(R.string.error_io, TagErrorText.of(TagError.Io("  ")).message)
        assertTrue(R.string.error_io != R.string.error_tag_lost_body, "a lost tag and a comms error must read differently")
    }
}
