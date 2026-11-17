package com.tagforge.domain.history

/**
 * Removes credentials from a URL before it is shown in a history line or copied into an export (S10): the user-info part
 * (`user:password@`) and the value of query/fragment parameters that conventionally carry a secret. Anything that is not a
 * hierarchical URL (`tel:`, plain text) is returned untouched, and the rest of the URL keeps its shape so the line is
 * still recognisable.
 */
object UrlRedaction {

    const val MASK = "***"

    private val SENSITIVE_KEYS = setOf(
        "token", "key", "apikey", "api_key", "access_token", "password", "pwd", "secret", "sig", "signature", "auth",
    )
    private val HIERARCHICAL = Regex("^([A-Za-z][A-Za-z0-9+.-]*://)([^/?#]*)(.*)$", RegexOption.DOT_MATCHES_ALL)

    fun redact(url: String): String {
        val match = HIERARCHICAL.matchEntire(url) ?: return url
        val (scheme, authority, rest) = match.destructured
        val host = authority.substringAfterLast('@')
        val path = rest.substringBefore('#')
        val fragment = if ('#' in rest) rest.substringAfter('#') else null
        val query = if ('?' in path) path.substringAfter('?') else null
        val pathOnly = path.substringBefore('?')
        return buildString {
            append(scheme).append(host).append(pathOnly)
            if (query != null) append('?').append(maskParameters(query))
            if (fragment != null) append('#').append(maskParameters(fragment))
        }
    }

    private fun maskParameters(parameters: String): String = parameters.split('&').joinToString("&") { parameter ->
        val key = parameter.substringBefore('=')
        if ('=' in parameter && key.lowercase() in SENSITIVE_KEYS) "$key=$MASK" else parameter
    }
}
