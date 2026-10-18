package com.tagforge.domain.task

/** The host a web link really goes to, for showing next to the full address (S2). */
object UrlHost {
    private val AUTHORITY = Regex("^https?://([^/?#]+)", RegexOption.IGNORE_CASE)

    /** The raw authority of an http(s) URL, or null for any other URL. */
    internal fun authorityOf(url: String): String? = AUTHORITY.find(url)?.groupValues?.get(1)

    /** `host` or `host:port` in lower case for an http(s) URL; null for tel:, mailto: and the like. */
    fun of(url: String): String? = authorityOf(url)?.lowercase()
}
