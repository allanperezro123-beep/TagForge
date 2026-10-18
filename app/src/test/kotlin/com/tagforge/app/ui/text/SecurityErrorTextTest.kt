package com.tagforge.app.ui.text

import com.tagforge.app.R
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.security.Check
import com.tagforge.domain.security.SecurityFailure
import com.tagforge.domain.security.SecurityOperation
import com.tagforge.domain.tag.TagError
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** G1 for the NS failures: each one has a string in en AND es, with the placeholders its arguments need. */
class SecurityErrorTextTest {

    private val everyFailure: List<SecurityFailure> = listOf(
        SecurityFailure.UidMismatch,
        SecurityFailure.ConfigLocked,
        SecurityFailure.Unremovable,
        SecurityFailure.AuthExhausted,
        SecurityFailure.AdvancedRequired,
        SecurityFailure.UnsupportedChip,
        SecurityFailure.NotProtected,
        SecurityFailure.PasswordRequired,
        SecurityFailure.Auth0OutOfRange,
        SecurityFailure.CannotMakeReadOnly,
        SecurityFailure.NothingToChange,
        SecurityFailure.WrongPlan,
        SecurityFailure.StateChanged,
        SecurityFailure.NotVerified(Check.NdefReadOnly),
        SecurityFailure.Partial(SecurityOperation.SET_PASSWORD, TagError.TagLost, 1, 4),
        SecurityFailure.Partial(SecurityOperation.REMOVE_PASSWORD, TagError.TagLost, 2, 5),
        SecurityFailure.Partial(SecurityOperation.LOCK, TagError.TagLost, 1, 3),
        SecurityFailure.Partial(SecurityOperation.CONFIG_LOCK, TagError.TagLost, 0, 1),
        SecurityFailure.Partial(SecurityOperation.RAW_WRITE, TagError.Io(null), 0, 1),
    )

    private fun stringsOf(dir: String): Map<String, String> {
        val file = listOf(File("src/main/res/$dir/strings.xml"), File("app/src/main/res/$dir/strings.xml")).first { it.isFile }
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i) as org.w3c.dom.Element
            node.getAttribute("name") to node.textContent
        }
    }

    private fun keyNameOf(resId: Int): String? =
        R.string::class.java.declaredFields.firstOrNull { it.type == Int::class.javaPrimitiveType && it.getInt(null) == resId }?.name

    private fun placeholderCount(text: String): Int = Regex("""%(\d+)\$""").findAll(text).map { it.groupValues[1].toInt() }.maxOrNull() ?: 0

    @Test
    fun theListCoversEveryFailureVariant() {
        val variants = assertNotNull(SecurityFailure::class.java.permittedSubclasses, "SecurityFailure must be sealed").size
        assertEquals(variants, everyFailure.map { it::class.java }.distinct().size)
    }

    @Test
    fun everyFailureHasATextInBothLanguagesWithMatchingPlaceholders() {
        val en = stringsOf("values")
        val es = stringsOf("values-es")
        for (failure in everyFailure) {
            val text = SecurityErrorText.of(failure)
            val key = keyNameOf(text.message)
            assertNotNull(key, "no R.string for $failure")
            assertTrue(key in en && key in es, "$key missing in a locale")
            assertEquals(text.args.size, placeholderCount(en.getValue(key)), "$key (en) placeholders")
            assertEquals(text.args.size, placeholderCount(es.getValue(key)), "$key (es) placeholders")
        }
    }

    @Test
    fun aPartialFailureCarriesHowFarItGotAndNeverReadsLikeSuccess() {
        val text = SecurityErrorText.of(SecurityFailure.Partial(SecurityOperation.SET_PASSWORD, TagError.TagLost, 1, 4))

        assertEquals(R.string.error_sec_partial_set_password, text.message)
        assertEquals(listOf<Any>(1, 4), text.args)
        assertEquals(R.string.error_sec_partial_lock, SecurityErrorText.of(SecurityFailure.Partial(SecurityOperation.LOCK, TagError.TagLost, 1, 3)).message)
        assertEquals(R.string.error_sec_partial_set_password, TagErrorText.of(TagError.Security(SecurityFailure.Partial(SecurityOperation.SET_PASSWORD, TagError.TagLost, 1, 4))).message)
    }
}
