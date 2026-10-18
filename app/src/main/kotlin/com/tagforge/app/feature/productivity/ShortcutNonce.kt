package com.tagforge.app.feature.productivity

import java.security.MessageDigest
import kotlin.random.Random

/**
 * The per-install secret that a pinned-template shortcut carries so that it, and only it, may start waiting for a tag on
 * open. MainActivity is exported, so any app can send it a `tagforge://` link; without this secret such a link only opens
 * the Write review and the user still has to press Write.
 */
object ShortcutNonce {
    private const val BYTES = 32

    /** 256 random bits as 64 lowercase hex digits. [random] must be a CSPRNG in production. */
    fun generate(random: Random): String =
        random.nextBytes(BYTES).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    /** True only when both are present, non-empty and equal. Compared in constant time for equal-length inputs. */
    fun matches(expected: String?, presented: String?): Boolean {
        if (expected.isNullOrEmpty() || presented.isNullOrEmpty()) return false
        return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), presented.toByteArray(Charsets.UTF_8))
    }
}
