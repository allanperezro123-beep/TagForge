package com.tagforge.domain.ndef

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome

/** Why a byte sequence could not be parsed as an NDEF message. Typed so UI can explain; never thrown. */
sealed interface NdefParseError {
    /** Header or payload ends before its declared length. */
    data object Truncated : NdefParseError

    /** TNF 7 is reserved. */
    data class InvalidTnf(val code: Int) : NdefParseError

    /** First record lacks the Message Begin flag. */
    data object MissingMessageBegin : NdefParseError

    /** Data ended without a record carrying the Message End flag. */
    data object MissingMessageEnd : NdefParseError

    /** A chunk sequence was started or continued incorrectly. */
    data object InvalidChunk : NdefParseError

    /** The Type 2 TLV area holds no NDEF TLV (blank, zeroed or terminator first). */
    data object NoNdefTlv : NdefParseError

    /** A TLV with a tag this parser cannot skip safely. */
    data class InvalidTlv(val tag: Int) : NdefParseError
}

interface NdefCodec {
    fun encode(message: NdefMessage): Bytes
    fun decode(bytes: Bytes): Outcome<NdefMessage, NdefParseError>
}
