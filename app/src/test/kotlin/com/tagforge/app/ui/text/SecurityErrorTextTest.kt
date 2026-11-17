package com.tagforge.app.ui.text

import com.tagforge.app.R
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.security.Check
import com.tagforge.domain.security.PasswordPageState
import com.tagforge.domain.security.ProtectionState
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
        SecurityFailure.AuthOutcomeUnknown,
        SecurityFailure.PackMismatch,
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

    private fun partial(operation: SecurityOperation, protection: ProtectionState, page: PasswordPageState, completed: Int = 1) =
        SecurityFailure.Partial(operation, TagError.TagLost, completed, 4, failedStep = completed, protection = protection, passwordPage = page)

    private val everyPartialVariant: List<SecurityFailure> = buildList {
        for (protection in ProtectionState.entries) for (page in PasswordPageState.entries) {
            add(partial(SecurityOperation.SET_PASSWORD, protection, page))
            add(partial(SecurityOperation.REMOVE_PASSWORD, protection, page))
        }
    }

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
    fun everyPartialSetOrRemoveVariantHasATextInBothLanguagesWithItsPlaceholders() {
        val en = stringsOf("values")
        val es = stringsOf("values-es")
        for (failure in everyPartialVariant) {
            val text = SecurityErrorText.of(failure)
            val key = assertNotNull(keyNameOf(text.message), "no R.string for $failure")
            assertTrue(key in en && key in es, "$key missing in a locale")
            assertEquals(2, placeholderCount(en.getValue(key)), "$key (en) placeholders")
            assertEquals(2, placeholderCount(es.getValue(key)), "$key (es) placeholders")
        }
    }

    @Test
    fun aSetPasswordInterruptionSaysWhetherTheTagIsProtectedAndWhetherThePasswordPageChanged() {
        fun key(protection: ProtectionState, page: PasswordPageState) =
            keyNameOf(SecurityErrorText.of(partial(SecurityOperation.SET_PASSWORD, protection, page)).message)

        assertEquals("error_sec_partial_set_not_protected_changed", key(ProtectionState.NOT_PROTECTED, PasswordPageState.CHANGED))
        assertEquals("error_sec_partial_set_not_protected", key(ProtectionState.NOT_PROTECTED, PasswordPageState.UNCHANGED))
        assertEquals("error_sec_partial_set_not_protected_maybe", key(ProtectionState.NOT_PROTECTED, PasswordPageState.UNKNOWN))
        assertEquals("error_sec_partial_set_protected", key(ProtectionState.PROTECTED, PasswordPageState.CHANGED))
        assertEquals("error_sec_partial_set_password", key(ProtectionState.UNKNOWN, PasswordPageState.CHANGED))
    }

    @Test
    fun aRemovalInterruptionSaysWhetherTheTagIsStillProtected() {
        fun key(protection: ProtectionState) =
            keyNameOf(SecurityErrorText.of(partial(SecurityOperation.REMOVE_PASSWORD, protection, PasswordPageState.UNCHANGED)).message)

        assertEquals("error_sec_partial_remove_open", key(ProtectionState.NOT_PROTECTED))
        assertEquals("error_sec_partial_remove_still_protected", key(ProtectionState.PROTECTED))
        assertEquals("error_sec_partial_remove", key(ProtectionState.UNKNOWN))
    }

    @Test
    fun anUnknownAuthOutcomeSaysTheAttemptMayHaveCountedAndTellsToCheckThePassword() {
        val text = SecurityErrorText.of(SecurityFailure.AuthOutcomeUnknown)
        val key = assertNotNull(keyNameOf(text.message))

        assertEquals("error_sec_auth_outcome_unknown", key)
        assertTrue(stringsOf("values").getValue(key).contains("may already count"), "EN says the attempt may have counted")
        assertTrue(stringsOf("values-es").getValue(key).contains("ya cuente"), "ES says the attempt may have counted")
    }

    @Test
    fun aPackMismatchSaysItMayBeACopyAndWasForgotten() {
        val text = SecurityErrorText.of(SecurityFailure.PackMismatch)
        val key = assertNotNull(keyNameOf(text.message))

        assertEquals("error_sec_pack_mismatch", key)
        assertTrue("forgotten" in stringsOf("values").getValue(key).lowercase(), "EN says it was forgotten")
        assertTrue("olvid" in stringsOf("values-es").getValue(key).lowercase(), "ES says it was forgotten")
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
