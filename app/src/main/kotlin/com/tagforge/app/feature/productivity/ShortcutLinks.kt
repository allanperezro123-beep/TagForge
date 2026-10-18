package com.tagforge.app.feature.productivity

import com.tagforge.app.navigation.Route

/** Where a launcher shortcut or a widget button leads. Deliberately small: none of these writes anything unasked. */
sealed interface ShortcutTarget {
    data object Scan : ShortcutTarget
    data object Write : ShortcutTarget

    /** A pinned template: open the Write screen with it loaded and waiting for a tag. */
    data class WriteTemplate(val templateId: Long) : ShortcutTarget
}

/**
 * The link carried by every shortcut and widget button (`tagforge://scan`, `tagforge://write`,
 * `tagforge://write/template/42`). A link is plain data, so the mapping is testable without Android and an unknown
 * or malformed one is simply ignored.
 */
object ShortcutLinks {
    const val SCHEME = "tagforge"

    fun uri(target: ShortcutTarget): String = when (target) {
        ShortcutTarget.Scan -> "$SCHEME://scan"
        ShortcutTarget.Write -> "$SCHEME://write"
        is ShortcutTarget.WriteTemplate -> "$SCHEME://write/template/${target.templateId}"
    }

    fun parse(uri: String?): ShortcutTarget? {
        val rest = uri?.removePrefixIgnoreCase("$SCHEME://") ?: return null
        val parts = rest.split('/')
        return when {
            parts == listOf("scan") -> ShortcutTarget.Scan
            parts == listOf("write") -> ShortcutTarget.Write
            parts.size == 3 && parts[0] == "write" && parts[1] == "template" ->
                parts[2].takeIf { id -> id.isNotEmpty() && id.all(Char::isDigit) }?.toLongOrNull()?.let(ShortcutTarget::WriteTemplate)
            else -> null
        }
    }

    fun route(target: ShortcutTarget): Route = when (target) {
        ShortcutTarget.Scan -> Route.Read
        ShortcutTarget.Write -> Route.Write()
        is ShortcutTarget.WriteTemplate -> Route.Write(templateId = target.templateId, armOnOpen = true)
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String? =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else null
}
