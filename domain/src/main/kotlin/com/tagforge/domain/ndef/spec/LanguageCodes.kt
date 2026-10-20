package com.tagforge.domain.ndef.spec

/** Language tags for the Text record: what the picker offers first, what counts as valid, and how the phone's language maps to one. */
object LanguageCodes {

    /** The languages shown before the user searches, most useful first. */
    val COMMON: List<String> = listOf("es", "en", "pt", "fr", "de", "it", "ca", "gl", "eu", "zh", "ja", "ko", "ar", "ru", "hi")

    private val CODE = Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$")
    private const val SCRIPT_LENGTH = 4
    private const val REGION_LENGTH = 2
    private const val FALLBACK = "en"

    /** The same rule the Text form validates with: a 2-3 letter language, then optional `-` subtags. */
    fun isValid(code: String): Boolean = CODE.matches(code)

    /** `ES_mx` -> `es-MX`: language lower case, a 4-letter script in title case, a 2-letter region in upper case. */
    fun normalize(code: String): String {
        val parts = code.trim().replace('_', '-').split('-').filter { it.isNotEmpty() }
        return parts.mapIndexed { index, part ->
            when {
                index == 0 -> part.lowercase()
                part.length == SCRIPT_LENGTH && part.all(Char::isLetter) -> part.lowercase().replaceFirstChar(Char::uppercase)
                part.length == REGION_LENGTH && part.all(Char::isLetter) -> part.uppercase()
                else -> part.lowercase()
            }
        }.joinToString("-")
    }

    /** The tag for the phone's language ([tag] is what `Locale.toLanguageTag()` returns), or `en` when there is none. */
    fun fromDeviceTag(tag: String): String {
        val normalized = normalize(tag)
        val undetermined = normalized == "und" || normalized.startsWith("und-")
        return if (isValid(normalized) && !undetermined) normalized else FALLBACK
    }
}
