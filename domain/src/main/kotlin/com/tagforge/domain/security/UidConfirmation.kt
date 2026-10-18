package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes

/** The typed confirmation of NS-01: the last 4 hex characters of the tag UID, in any case. */
object UidConfirmation {
    const val LENGTH = 4

    fun expected(uid: Bytes): String = uid.toHex().takeLast(LENGTH)

    fun matches(uid: Bytes, typed: String): Boolean {
        val cleaned = typed.filterNot { it.isWhitespace() || it == ':' }.uppercase()
        return cleaned.length == LENGTH && cleaned == expected(uid)
    }
}
