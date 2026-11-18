package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes

/** The password to send to one tag, and the PACK that tag is expected to answer when the source knows it. */
data class ResolvedPassword(val password: Password, val pack: Bytes?)

/**
 * Where the password for a tag comes from. [Typed] is the same for every tag; [Diversified] is calculated from a master
 * passphrase and the UID of the tag that answers at tap time (WU4), so the UI never needs the UID before the tap.
 * Neither prints its secret.
 */
sealed interface PasswordSource {

    fun forTag(uid: Bytes): ResolvedPassword

    class Typed(private val password: Password) : PasswordSource {
        override fun forTag(uid: Bytes): ResolvedPassword = ResolvedPassword(password, pack = null)

        override fun toString(): String = "PasswordSource.Typed(****)"
    }

    /** [passphrase] is trusted to have passed [DiversifiedPassword.check]; it is kept only for the life of this object. */
    class Diversified(private val passphrase: String) : PasswordSource {
        override fun forTag(uid: Bytes): ResolvedPassword {
            val (password, pack) = DiversifiedPassword.derive(passphrase, uid)
            return ResolvedPassword(password, pack)
        }

        override fun toString(): String = "PasswordSource.Diversified(****)"
    }
}
