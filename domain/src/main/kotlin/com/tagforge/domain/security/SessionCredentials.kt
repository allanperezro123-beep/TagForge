package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The password of one tag plus, when it is known, the PACK that tag must answer (a different one means a clone). Never printed. */
class Credential(val password: Password, val pack: Bytes?) {
    override fun toString(): String = "Credential(****)"
}

/**
 * Passwords the user typed in this app session, kept so the NEXT operations on the same tag (read, write, erase, dump, clone)
 * can authenticate by themselves.
 *
 * Security properties, all deliberate:
 * - Memory only. Nothing here is persisted, exported, logged or put in the history, and the store dies with the process.
 * - One entry per tag UID. The password is only ever sent (by [UnlockedTagSession]) to a tag that reports that same UID,
 *   during an operation the user started.
 * - It expires [TTL] after it was remembered (not extended by use): a short window, because the password travels in clear
 *   over the air and a cloned-UID tag could capture it. Expired entries are dropped the next time the store is touched.
 * - [toString] and [Credential.toString] print no secret.
 */
class SessionCredentials(private val clock: Clock, private val ttl: Duration = TTL) {

    private class Entry(val credential: Credential, val expiresAt: Instant)

    private val lock = Any()
    private val entries = LinkedHashMap<Bytes, Entry>()
    private val _liveCount = MutableStateFlow(0)

    /** Live count of remembered tags (no UIDs, no secrets). Refreshed on every store access. */
    val liveCount: StateFlow<Int> = _liveCount.asStateFlow()

    fun remember(uid: Bytes, password: Password, pack: Bytes? = null) {
        synchronized(lock) {
            purgeExpired()
            entries.remove(uid)
            entries[uid] = Entry(Credential(password, pack), clock.now() + ttl)
            _liveCount.value = entries.size
        }
    }

    /** Null when nothing is remembered for [uid] (or it expired). */
    fun credentialFor(uid: Bytes): Credential? = synchronized(lock) {
        purgeExpired()
        entries[uid]?.credential
    }

    fun forget(uid: Bytes) {
        synchronized(lock) {
            entries.remove(uid)
            _liveCount.value = entries.size
        }
    }

    fun forgetAll() {
        synchronized(lock) {
            entries.clear()
            _liveCount.value = 0
        }
    }

    /** The UIDs with a live entry, oldest first. For the screens that say "a password is remembered"; never the passwords. */
    fun rememberedUids(): List<Bytes> = synchronized(lock) {
        purgeExpired()
        entries.keys.toList()
    }

    fun hasAny(): Boolean = rememberedUids().isNotEmpty()

    override fun toString(): String = "SessionCredentials(entries=****)"

    private fun purgeExpired() {
        val now = clock.now()
        entries.values.removeAll { it.expiresAt <= now }
        _liveCount.value = entries.size
    }

    companion object {
        /** How long a remembered password lives. */
        val TTL: Duration = 10.minutes
    }
}
