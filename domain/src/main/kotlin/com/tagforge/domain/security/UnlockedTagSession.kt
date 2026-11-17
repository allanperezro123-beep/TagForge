package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.tag.TagTech

/**
 * A [TagSession] whose raw NfcA channel authenticates by itself with the password the user remembered for this tag
 * ([SessionCredentials]), so read, write, erase, format, dump and clone work on a tag whose memory is behind a password.
 *
 * Why a decorator on the channel and not a call inside every use case: the PWD_AUTH state lives only as long as the RF
 * connection, and on a phone any technology switch (Ndef <-> NfcA) re-selects the tag and ends it. The channel is the one
 * place that sees every connection start, so it authenticates right before the first command after each (re)connect and
 * never again while the link stays up. The use cases stay unaware of passwords (they are unchanged).
 *
 * Security: the remembered password is sent only to a tag reporting the UID it was remembered for, only by this decorator
 * (which the [com.tagforge.domain.scan.ScanCoordinator] applies to operations the user started, never to the security
 * operations that type their own password), and in clear over the air. A tag that clones the UID could capture it; this is
 * why [SessionCredentials] is memory-only and short-lived. A failed attempt forgets the password (see [TagUnlock]).
 */
class UnlockedTagSession(private val delegate: TagSession, credentials: SessionCredentials) : TagSession by delegate {

    private val unlocking: TagTransceiver? by lazy {
        delegate.transceiver(TagTech.NFC_A)?.let { UnlockingTransceiver(it, delegate.info.uid, credentials) }
    }

    override fun transceiver(tech: TagTech): TagTransceiver? = if (tech == TagTech.NFC_A) unlocking else delegate.transceiver(tech)
}

/** Sends the one PWD_AUTH ([TagUnlock]) before the first command of each connection; a typed PWD_AUTH passes through untouched. */
private class UnlockingTransceiver(
    private val inner: TagTransceiver,
    private val uid: Bytes,
    private val credentials: SessionCredentials,
) : TagTransceiver by inner {

    /** True until this connection authenticated. A link error or a reconnect puts it back: the tag forgot the password. */
    private var needsUnlock = true

    override suspend fun transceive(command: Bytes): TagResult<Bytes> {
        if (command.isEmpty() || command[0] == NtagCommands.PWD_AUTH) return inner.transceive(command)
        if (!inner.isConnected) needsUnlock = true
        if (needsUnlock) {
            when (val unlocked = TagUnlock.ensureUnlocked(inner, uid, credentials)) {
                is Outcome.Err -> return Outcome.Err(unlocked.error)
                is Outcome.Ok -> needsUnlock = false
            }
        }
        val result = inner.transceive(command)
        if (result is Outcome.Err) needsUnlock = true
        return result
    }

    override suspend fun reconnect(): TagResult<Unit> {
        needsUnlock = true
        return inner.reconnect()
    }
}
