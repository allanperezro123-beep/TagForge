package com.tagforge.app.feature.write.forms

import com.tagforge.domain.ndef.spec.LanguageCodes
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanguageOptionsTest {

    private val english = Locale.ENGLISH

    @Test
    fun theCommonListKeepsTheDomainOrderAndShowsNativeAndLocalizedNames() {
        val common = LanguageOptions.common(english)

        assertEquals(LanguageCodes.COMMON, common.map { it.code })
        val spanish = common.first()
        assertEquals("Español", spanish.nativeName)
        assertEquals("Spanish", spanish.localizedName)
    }

    @Test
    fun searchFindsALanguageByItsLocalizedNameItsNativeNameOrItsCode() {
        assertTrue("es" in LanguageOptions.search("span", english).map { it.code })
        assertTrue("es" in LanguageOptions.search("espan", english).map { it.code }, "accents do not matter")
        assertTrue("ja" in LanguageOptions.search("JA", english).map { it.code })
    }

    @Test
    fun searchReachesLanguagesThatAreNotInTheShortList() {
        val codes = LanguageOptions.search("swahili", english).map { it.code }

        assertTrue("sw" in codes)
        assertTrue("sw" !in LanguageCodes.COMMON)
    }

    @Test
    fun withoutAQueryTheShortListComesFirstThenEveryOtherLanguage() {
        val all = LanguageOptions.search("", english)

        assertEquals(LanguageCodes.COMMON, all.take(LanguageCodes.COMMON.size).map { it.code })
        assertTrue(all.size > 100)
        assertEquals(all.size, all.map { it.code }.toSet().size, "no language is listed twice")
    }

    @Test
    fun describeKeepsTheRegionOfTheCurrentValue() {
        val colombian = LanguageOptions.describe("es-CO", english)

        assertEquals("es-CO", colombian.code)
        assertEquals("Spanish (Colombia)", colombian.localizedName)
        assertTrue(colombian.nativeName.startsWith("Español"))
    }

    @Test
    fun describeOfAnUnknownCodeStillReturnsSomethingReadable() {
        val odd = LanguageOptions.describe("qq", english)

        assertEquals("qq", odd.code)
        assertTrue(odd.localizedName.isNotBlank())
    }
}
