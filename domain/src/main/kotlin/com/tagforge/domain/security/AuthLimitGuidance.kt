package com.tagforge.domain.security

/** What one NTAG21x AUTHLIM 3-bit value (0..7) means for consecutive failed authentications. */
sealed interface AuthLimitMeaning {
    /** AUTHLIM = 0: leaves the chip's limit behavior unchanged (disabled on a factory tag). */
    data object LeavesUnchanged : AuthLimitMeaning

    /** AUTHLIM = 1..7: linear maximum consecutive bad attempts before the password is locked permanently. */
    data class AtMostAttempts(val attempts: Int) : AuthLimitMeaning
}

/**
 * Interprets the 3-bit AUTHLIM field of NTAG21x ACCESS (datasheet section 8.5.7).
 *
 * AUTHLIM is a linear count (1..7 wrong attempts, NOT 2^n), reset by a valid authentication; 0 disables the limit.
 */
object AuthLimitGuidance {
    const val MAX_VALUE: Int = 7

    /** Middle-of-range default: deters guessing while leaving headroom for honest typos before permanent lock. */
    const val RECOMMENDED: Int = 5

    fun meaning(authLimit: Int): AuthLimitMeaning {
        require(authLimit in 0..MAX_VALUE) { "AUTHLIM must fit in 3 bits (0..$MAX_VALUE), was $authLimit" }
        return if (authLimit == 0) AuthLimitMeaning.LeavesUnchanged else AuthLimitMeaning.AtMostAttempts(authLimit)
    }
}
