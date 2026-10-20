package com.tagforge.domain.location

import java.net.URLDecoder

/** What a pasted or shared map text turned out to be. */
sealed interface MapLink {
    /** A point on the map. [latitude] and [longitude] are kept as written in the link; [label] is a place name when the link had one. */
    data class Coordinates(val latitude: String, val longitude: String, val label: String = "") : MapLink

    /** A link without coordinates (typically a short link): kept as the web address, which opens Maps when scanned. */
    data class Web(val url: String, val title: String = "") : MapLink

    data object Unrecognized : MapLink
}

/**
 * Extracts a place from the ways people hand over a location: Google or Apple Maps links, `geo:` URIs, coordinates typed
 * by hand, and the "name + short link" text Google Maps shares. Only http, https and geo are ever accepted.
 */
object MapLinkParser {

    private const val NUMBER = "-?\\d{1,3}(?:\\.\\d+)?"
    private const val MAX_LAT = 90.0
    private const val MAX_LON = 180.0
    private const val MAX_TITLE = 120
    private val PAIR = Regex("^\\s*($NUMBER)\\s*,\\s*($NUMBER)\\s*(?:\\((.*)\\))?\\s*$")
    private val PIN = Regex("!3d($NUMBER)!4d($NUMBER)")
    private val VIEWPORT = Regex("/@($NUMBER),($NUMBER)")
    private val WEB_URL = Regex("(?<!\\S)https?://\\S+", RegexOption.IGNORE_CASE)
    private val AUTHORITY = Regex("^https?://([^/?#]+)", RegexOption.IGNORE_CASE)
    private val PLACE = Regex("/place/([^/@?]+)")
    private val COORDINATE_KEYS = listOf("ll", "sll", "q", "query", "center", "destination", "daddr")

    fun parse(text: String): MapLink {
        val trimmed = text.trim()
        return when {
            trimmed.isEmpty() -> MapLink.Unrecognized
            trimmed.startsWith("geo:", ignoreCase = true) -> geo(trimmed.substring("geo:".length)) ?: MapLink.Unrecognized
            PAIR.matches(trimmed) -> pair(trimmed) ?: MapLink.Unrecognized
            else -> web(trimmed)
        }
    }

    private fun pair(text: String): MapLink.Coordinates? = PAIR.matchEntire(text)?.let { m ->
        coordinates(m.groupValues[1], m.groupValues[2], m.groupValues[3])
    }

    private fun geo(body: String): MapLink.Coordinates? {
        val path = body.substringBefore('?').substringBefore(';')
        val query = queryOf(body)
        val fromQuery = query["q"]?.let(::pair)
        val fromPath = pair(path)
        val atOrigin = fromPath != null && fromPath.latitude.toDouble() == 0.0 && fromPath.longitude.toDouble() == 0.0
        return when {
            fromPath != null && !atOrigin -> fromPath.copy(label = fromQuery?.label ?: query["q"]?.takeIf { pair(it) == null }.orEmpty())
            else -> fromQuery
        }
    }

    private fun web(text: String): MapLink {
        val match = WEB_URL.find(text) ?: return MapLink.Unrecognized
        val url = match.value.trimEnd('.', ',', ';', '!')
        val authority = AUTHORITY.find(url)?.groupValues?.get(1) ?: return MapLink.Unrecognized
        // `https://trusted.com@evil.com` shows one host and opens another: never accepted.
        if (authority.any { it == '@' || it == '\\' }) return MapLink.Unrecognized

        val found = coordinatesIn(url)
        if (found != null) return found
        return MapLink.Web(url, title = text.substring(0, match.range.first).trim().lineSequence().lastOrNull { it.isNotBlank() }.orEmpty().trim().take(MAX_TITLE))
    }

    private fun coordinatesIn(url: String): MapLink.Coordinates? {
        val label = PLACE.find(url)?.groupValues?.get(1)?.let(::decode).orEmpty()
        PIN.find(url)?.let { m -> coordinates(m.groupValues[1], m.groupValues[2], label)?.let { return it } }
        val query = queryOf(url.substringBefore('#'))
        for (key in COORDINATE_KEYS) {
            val value = query[key] ?: continue
            pair(value)?.let { return if (it.label.isEmpty()) it.copy(label = label) else it }
        }
        VIEWPORT.find(url)?.let { m -> coordinates(m.groupValues[1], m.groupValues[2], label)?.let { return it } }
        return null
    }

    private fun coordinates(latitude: String, longitude: String, label: String): MapLink.Coordinates? {
        val lat = latitude.toDoubleOrNull() ?: return null
        val lon = longitude.toDoubleOrNull() ?: return null
        if (lat !in -MAX_LAT..MAX_LAT || lon !in -MAX_LON..MAX_LON) return null
        return MapLink.Coordinates(latitude, longitude, label.trim())
    }

    private fun queryOf(url: String): Map<String, String> {
        val mark = url.indexOf('?')
        if (mark < 0) return emptyMap()
        return url.substring(mark + 1).split('&').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq).lowercase() to decode(part.substring(eq + 1))
        }.toMap()
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        value
    }
}
