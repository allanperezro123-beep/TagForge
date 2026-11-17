package com.tagforge.domain.task

/**
 * Decides, from the text of a URL authority alone, whether a host is a device inside the user's own network (router,
 * NAS, camera, smart plug, the phone itself). Pure string work: no DNS, no `java.net` resolution.
 *
 * Known and accepted limit: a public-looking NAME that resolves to a private address (`evil.example.com` -> 192.168.1.1,
 * or a rebinding trick) is NOT detected, because the domain never resolves names. The check is a speed bump for the
 * obvious targets, not a firewall.
 *
 * Treated as local, case-insensitively and ignoring a trailing dot and the port:
 * - IPv4 in 10/8, 172.16/12, 192.168/16, 127/8, 169.254/16, 100.64/10, 0/8 and 255.255.255.255;
 * - any numeric spelling that is not a plain dotted quad (`2130706433`, `0x7f.0.0.1`, `0177.0.0.1`, `127.1`), because HTTP
 *   clients accept them and they are only ever used to hide an address;
 * - IPv6 `::`, `::1`, fc00::/7, fe80::/10 and IPv4-mapped or -compatible forms of a local IPv4;
 * - `localhost`, `*.localhost`, `*.local`, `*.lan`, `*.home`, `*.internal`, `*.intranet`, `*.home.arpa` and names without a dot.
 */
object LocalNetworkHost {

    private val LOCAL_SUFFIXES = listOf(".localhost", ".local", ".lan", ".home", ".internal", ".intranet", ".home.arpa")
    private val NUMERIC_PART = Regex("^(?:[0-9]+|0x[0-9a-f]*)$")
    private val CANONICAL_OCTET = Regex("^(?:0|[1-9][0-9]{0,2})$")

    private const val OCTET_MAX = 255
    private const val GROUP_COUNT = 8
    private const val GROUP_MAX_DIGITS = 4
    private const val HEX = 16
    private const val MAPPED_MARKER = 0xFFFF

    /** [authority] is `host`, `host:port` or `[ipv6]:port`, as in [UrlHost.of]. */
    fun isLocal(authority: String): Boolean {
        val text = authority.trim().lowercase()
        if (text.startsWith("[")) {
            val literal = text.substring(1).substringBefore(']').substringBefore('%')
            return parseIpv6(literal)?.let(::isLocalIpv6) ?: true   // an unreadable literal is not worth the benefit of the doubt
        }
        val host = text.substringBefore(':').trimEnd('.')
        return when {
            host.isEmpty() -> false
            host.split('.').all { NUMERIC_PART.matches(it) } -> canonicalQuad(host)?.let(::isLocalIpv4) ?: true
            else -> host == "localhost" || host == "home.arpa" || LOCAL_SUFFIXES.any { host.endsWith(it) } || '.' !in host
        }
    }

    private fun isLocalIpv4(o: IntArray): Boolean {
        val (a, b) = o[0] to o[1]
        return a == 10 || a == 127 || a == 0 ||
            (a == 169 && b == 254) || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 100 && b in 64..127) ||
            o.all { it == OCTET_MAX }
    }

    /** The four octets of a plain decimal dotted quad (no leading zeros, each 0..255), or null for any other spelling. */
    private fun canonicalQuad(text: String): IntArray? {
        val parts = text.split('.')
        if (parts.size != 4 || parts.any { !CANONICAL_OCTET.matches(it) }) return null
        return parts.map { it.toInt() }.takeIf { octets -> octets.all { it <= OCTET_MAX } }?.toIntArray()
    }

    private fun isLocalIpv6(g: IntArray): Boolean {
        val zeroPrefix = (0 until 5).all { g[it] == 0 }
        return when {
            (0 until 7).all { g[it] == 0 } && g[7] <= 1 -> true                          // :: and ::1
            (g[0] and 0xFE00) == 0xFC00 || (g[0] and 0xFFC0) == 0xFE80 -> true            // fc00::/7 and fe80::/10
            zeroPrefix && g[5] == MAPPED_MARKER -> isLocalIpv4(embeddedIpv4(g))           // ::ffff:a.b.c.d
            zeroPrefix && g[5] == 0 -> isLocalIpv4(embeddedIpv4(g))                       // ::a.b.c.d (compatible)
            else -> false
        }
    }

    private fun embeddedIpv4(g: IntArray) = intArrayOf(g[6] shr 8, g[6] and 0xFF, g[7] shr 8, g[7] and 0xFF)

    /** The eight 16-bit groups of an IPv6 literal (without brackets), or null when it is not well formed. */
    private fun parseIpv6(text: String): IntArray? {
        var s = text
        var v4: IntArray? = null
        if ('.' in s) {
            val cut = s.lastIndexOf(':')
            v4 = canonicalQuad(s.substring(cut + 1)) ?: return null
            s = s.substring(0, cut + 1) + "0:0"
        }
        val halves = s.split("::")
        if (halves.size > 2) return null
        val compressed = halves.size == 2
        val head = groups(halves[0]) ?: return null
        val tail = if (compressed) groups(halves[1]) ?: return null else emptyList()
        val gap = GROUP_COUNT - head.size - tail.size   // the zero groups that "::" stands for
        if (if (compressed) gap < 1 else gap != 0) return null
        val all = (head + List(gap) { 0 } + tail).toIntArray()
        if (v4 != null) {
            all[6] = (v4[0] shl 8) or v4[1]
            all[7] = (v4[2] shl 8) or v4[3]
        }
        return all
    }

    private fun groups(part: String): List<Int>? {
        if (part.isEmpty()) return emptyList()
        return part.split(':').map { g -> if (g.isEmpty() || g.length > GROUP_MAX_DIGITS) return null else g.toIntOrNull(HEX) ?: return null }
    }
}
