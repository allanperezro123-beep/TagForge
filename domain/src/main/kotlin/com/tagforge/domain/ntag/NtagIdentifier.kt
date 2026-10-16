package com.tagforge.domain.ntag

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.tag.TagError

/** Detects the NTAG21x model with GET_VERSION. Shared by the Android adapter and the fakes. */
object NtagIdentifier {

    /**
     * Ok(model) for a recognised NTAG21x, Ok(null) for any tag that does not answer GET_VERSION like one
     * (NAK, short answer, I/O refusal). Only a vanished tag (or cancellation) is an error.
     */
    suspend fun identify(transceiver: TagTransceiver): TagResult<NtagModel?> =
        when (val result = transceiver.transceive(NtagCommands.getVersion())) {
            is Outcome.Ok -> Outcome.Ok(NtagResponses.parseVersion(result.value))
            is Outcome.Err -> when (result.error) {
                TagError.TagLost, TagError.Cancelled, TagError.Timeout -> result
                else -> Outcome.Ok(null)
            }
        }
}
