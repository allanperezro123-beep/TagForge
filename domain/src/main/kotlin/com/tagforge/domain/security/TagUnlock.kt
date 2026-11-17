package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.tag.TagError

/**
 * Authenticates with a password the user ALREADY typed and the app remembered ([SessionCredentials]), right before the raw
 * commands of an operation. It never invents a password: with nothing remembered for the tag it does nothing at all.
 *
 * What it sends is exactly one PWD_AUTH ([SecurityIo.authenticate]); there is no retry and no second attempt. The password
 * goes in clear over the air, so it is only sent to a tag that reports the very UID it was remembered for.
 */
internal object TagUnlock {

    /**
     * Ok when the tag is unlocked, or when there was nothing to send. Err when the single attempt failed: [TagError.AuthFailed]
     * (wrong password), AuthExhausted, AuthOutcomeUnknown (the link dropped, the attempt may have counted) or PackMismatch
     * (the tag answered another PACK than the one stored: possibly a clone). Every failure forgets the credential, so the
     * next operation on this tag sends nothing until the user unlocks again.
     */
    suspend fun ensureUnlocked(transceiver: TagTransceiver, uid: Bytes, credentials: SessionCredentials): TagResult<Unit> {
        val credential = credentials.credentialFor(uid) ?: return Outcome.Ok(Unit)
        return when (val answered = SecurityIo.authenticate(transceiver, credential.password.bytes)) {
            is Outcome.Err -> {
                credentials.forget(uid)
                Outcome.Err(answered.error)
            }
            is Outcome.Ok -> {
                val expected = credential.pack
                if (expected != null && expected != answered.value) {
                    credentials.forget(uid)
                    Outcome.Err(TagError.Security(SecurityFailure.PackMismatch))
                } else {
                    Outcome.Ok(Unit)
                }
            }
        }
    }
}
