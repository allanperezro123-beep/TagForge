package com.tagforge.app.feature.write.forms

import com.tagforge.app.ui.text.foldForSearch
import com.tagforge.domain.ndef.spec.LanguageCodes
import java.text.Collator
import java.util.Locale

/** A language as the picker shows it: the code that gets stored, its own name and its name in the phone's language. */
data class LanguageOption(val code: String, val nativeName: String, val localizedName: String)

/** The pure part of the language picker (names come from the JDK/Android locale data, so it also runs in unit tests). */
object LanguageOptions {

    /** The short list shown before any search, named in [display]'s language. */
    fun common(display: Locale): List<LanguageOption> = LanguageCodes.COMMON.map { describe(it, display) }

    /** The short list first, then every other language alphabetically; [query] keeps the ones whose names or code contain it. */
    fun search(query: String, display: Locale): List<LanguageOption> {
        val collator = Collator.getInstance(display).apply { strength = Collator.PRIMARY }
        val others = Locale.getISOLanguages()
            .filter { it !in LanguageCodes.COMMON }
            .map { describe(it, display) }
            .filter { it.localizedName != it.code }
            .sortedWith { a, b -> collator.compare(a.localizedName, b.localizedName) }
        val needle = query.trim().foldForSearch()
        val all = common(display) + others
        if (needle.isEmpty()) return all
        return all.filter { option ->
            option.code.startsWith(needle) ||
                needle in option.localizedName.foldForSearch() ||
                needle in option.nativeName.foldForSearch()
        }
    }

    /** The option for an arbitrary [code], for example the value already in the field ("es-CO"). */
    fun describe(code: String, display: Locale): LanguageOption {
        val locale = Locale.forLanguageTag(code)
        return LanguageOption(code, locale.nameIn(locale), locale.nameIn(display).ifBlank { code })
    }

    private fun Locale.nameIn(display: Locale): String = getDisplayName(display).replaceFirstChar { it.titlecase(display) }
}
