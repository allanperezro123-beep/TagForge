package com.tagforge.domain.tag

import com.tagforge.domain.common.Bytes

/** Every failure an NFC operation can produce. The domain never returns user-facing text. */
sealed interface TagError {
    data object TagLost : TagError
    data object Timeout : TagError
    data object Cancelled : TagError
    data object NotNdef : TagError
    data object ReadOnly : TagError
    data object AuthRequired : TagError
    data class Unsupported(val what: String) : TagError {
        companion object {
            /** A MIFARE Classic card on a phone without the NXP controller (or an operation Classic does not support). */
            const val MIFARE_CLASSIC = "mifare-classic"
        }
    }
    data class CapacityExceeded(val required: Int, val available: Int) : TagError
    data class AuthFailed(val attemptsLeft: Int?) : TagError
    data class VerifyMismatch(val expected: Bytes, val actual: Bytes?) : TagError
    data class Nak(val code: Int) : TagError
    data class Malformed(val detail: String) : TagError
    data class Io(val detail: String?) : TagError
}
