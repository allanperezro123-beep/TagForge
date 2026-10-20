package com.tagforge.domain.ndef.spec

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanguageCodesTest {

    @Test
    fun theCommonLanguagesAreTheOnesTheBriefListsAndAllAreValid() {
        assertEquals(
            listOf("es", "en", "pt", "fr", "de", "it", "ca", "gl", "eu", "zh", "ja", "ko", "ar", "ru", "hi"),
            LanguageCodes.COMMON,
        )
        assertTrue(LanguageCodes.COMMON.all(LanguageCodes::isValid))
    }

    @Test
    fun aValidCodeIsTwoOrThreeLettersOptionallyFollowedBySubtags() {
        assertTrue(LanguageCodes.isValid("es"))
        assertTrue(LanguageCodes.isValid("es-MX"))
        assertTrue(LanguageCodes.isValid("zh-Hans-CN"))
        assertTrue(LanguageCodes.isValid("fil"))
    }

    @Test
    fun wordsEmptyAndNumbersAreNotCodes() {
        assertFalse(LanguageCodes.isValid(""))
        assertFalse(LanguageCodes.isValid("e"))
        assertFalse(LanguageCodes.isValid("español"))
        assertFalse(LanguageCodes.isValid("12"))
        assertFalse(LanguageCodes.isValid("es_MX"))
    }

    @Test
    fun normalizeFixesCaseSeparatorsAndSpaces() {
        assertEquals("es-MX", LanguageCodes.normalize("ES_mx"))
        assertEquals("en", LanguageCodes.normalize("  EN "))
        assertEquals("zh-Hans-CN", LanguageCodes.normalize("zh-hans-cn"))
    }

    @Test
    fun theDeviceLanguageKeepsItsRegionBecauseThatIsWhatTheUserSpeaks() {
        assertEquals("es-CO", LanguageCodes.fromDeviceTag("es-CO"))
        assertEquals("pt-BR", LanguageCodes.fromDeviceTag("pt_BR"))
    }

    @Test
    fun anUnknownOrEmptyDeviceLanguageFallsBackToEnglishInsteadOfAnInvalidRecord() {
        assertEquals("en", LanguageCodes.fromDeviceTag(""))
        assertEquals("en", LanguageCodes.fromDeviceTag("und"))
        assertEquals("en", LanguageCodes.fromDeviceTag("???"))
    }
}
