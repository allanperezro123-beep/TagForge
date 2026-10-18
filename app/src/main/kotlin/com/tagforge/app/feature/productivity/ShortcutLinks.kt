package com.tagforge.app.feature.productivity

import com.tagforge.app.navigation.Route

/** Where a launcher shortcut or a widget button leads. Deliberately small: none of these writes anything unasked. */
sealed interface ShortcutTarget {
    data object Scan : ShortcutTarget
    data object Write : ShortcutTarget

    /**
     * A pinned template: open the Write screen with it loaded. [armOnOpen] (wait for a tag at once) is true only when the
     * link carried this install's nonce, see [ShortcutLinks.resolve]; a parsed link alone never sets it.
     */
    data class WriteTemplate(val templateId: Long, val armOnOpen: Boolean = false) : ShortcutTarget
}

/**
 * The link carried by every shortcut and widget button (`tagforge://scan`, `tagforge://write`,
 * `tagforge://write/template/42`). A link is plain data, so the mapping is testable without Android and an unknown
 * or malformed one is simply ignored.
 */
object ShortcutLinks {
    const val SCHEME = "tagforge"

    private const val NONCE_PARAM = "n="

    /** The link for [target]. Only a pinned template may carry the install's [nonce] (it is how it is allowed to arm). */
    fun uri(target: ShortcutTarget, nonce: String? = null): String = when (target) {
        ShortcutTarget.Scan -> "$SCHEME://scan"
        ShortcutTarget.Write -> "$SCHEME://write"
        is ShortcutTarget.WriteTemplate ->
            "$SCHEME://write/template/${target.templateId}" + if (nonce.isNullOrEmpty()) "" else "?$NONCE_PARAM$nonce"
    }

    /** The target of [uri], never armed: any query string is ignored. */
    fun parse(uri: String?): ShortcutTarget? {
        val rest = uri?.removePrefixIgnoreCase("$SCHEME://")?.substringBefore('?') ?: return null
        val parts = rest.split('/')
        return when {
            parts == listOf("scan") -> ShortcutTarget.Scan
            parts == listOf("write") -> ShortcutTarget.Write
            parts.size == 3 && parts[0] == "write" && parts[1] == "template" ->
                parts[2].takeIf { id -> id.isNotEmpty() && id.all(Char::isDigit) }?.toLongOrNull()?.let { ShortcutTarget.WriteTemplate(it) }
            else -> null
        }
    }

    /**
     * What an incoming link may do. MainActivity is exported, so the link is untrusted: a template link arms the write
     * only if it carries [expectedNonce] (the secret only pinned shortcuts embed); anything else opens the review.
     */
    fun resolve(uri: String?, expectedNonce: String?): ShortcutTarget? {
        val target = parse(uri) ?: return null
        if (target !is ShortcutTarget.WriteTemplate) return target
        val presented = uri?.substringAfter('?', "")?.split('&')?.firstOrNull { it.startsWith(NONCE_PARAM) }?.removePrefix(NONCE_PARAM)
        return target.copy(armOnOpen = ShortcutNonce.matches(expectedNonce, presented))
    }

    fun route(target: ShortcutTarget): Route = when (target) {
        ShortcutTarget.Scan -> Route.Read
        ShortcutTarget.Write -> Route.Write()
        is ShortcutTarget.WriteTemplate -> Route.Write(templateId = target.templateId, armOnOpen = target.armOnOpen)
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String? =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else null
}
