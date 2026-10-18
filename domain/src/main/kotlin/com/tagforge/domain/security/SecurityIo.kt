package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ntag.NtagResponses
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech

/** An identified NTAG21x ready for raw frames. */
internal class NtagLink(val info: TagInfo, val model: NtagModel, val transceiver: TagTransceiver)

/** Raw frames shared by the NS use cases. Maps NAK codes to the errors the security screens explain. */
internal object SecurityIo {
    private const val NAK_INVALID = 0x0
    private const val NAK_COUNTER_OVERFLOW = 0x4

    /** Identifies the tag; [TagError.Security] UnsupportedChip when it is not an NTAG21x reachable over NfcA. */
    suspend fun connect(session: TagSession): TagResult<NtagLink> {
        val info = when (val identified = session.identify()) {
            is Outcome.Err -> return identified
            is Outcome.Ok -> identified.value
        }
        val model = info.ntag
        val transceiver = session.transceiver(TagTech.NFC_A)
        if (model == null || transceiver == null) return Outcome.Err(TagError.Security(SecurityFailure.UnsupportedChip))
        return Outcome.Ok(NtagLink(info, model, transceiver))
    }

    /** FAST_READ [from]..[to]; a NAK means the pages are behind the password, so [TagError.AuthRequired]. */
    suspend fun readPages(t: TagTransceiver, from: Int, to: Int): TagResult<Bytes> {
        val response = when (val sent = t.transceive(NtagCommands.fastRead(from, to))) {
            is Outcome.Err -> return sent
            is Outcome.Ok -> sent.value
        }
        val checked = NtagResponses.checkData(response, (to - from + 1) * NtagCommands.PAGE_SIZE)
        return if (checked is Outcome.Err && checked.error is TagError.Nak) Outcome.Err(TagError.AuthRequired) else checked
    }

    /** PWD_AUTH: Ok(PACK) / Err(AuthFailed) for a wrong password / Err(Security(AuthExhausted)) once AUTHLIM ran out. */
    suspend fun authenticate(t: TagTransceiver, password: Bytes): TagResult<Bytes> {
        val response = when (val sent = t.transceive(NtagCommands.pwdAuth(password))) {
            is Outcome.Err -> return sent
            is Outcome.Ok -> sent.value
        }
        return when (NtagResponses.nakCode(response)) {
            NAK_INVALID -> Outcome.Err(TagError.AuthFailed(attemptsLeft = null))
            NAK_COUNTER_OVERFLOW -> Outcome.Err(TagError.Security(SecurityFailure.AuthExhausted))
            null -> NtagResponses.checkData(response, NtagCommands.PACK_SIZE)
            else -> Outcome.Err(TagError.Nak(response[0]))
        }
    }

    /** WRITE one page. Some controllers swallow the 4-bit ACK and answer nothing: that counts as acknowledged. */
    suspend fun writePage(t: TagTransceiver, page: Int, data: Bytes): TagResult<Unit> {
        val response = when (val sent = t.transceive(NtagCommands.write(page, data))) {
            is Outcome.Err -> return sent
            is Outcome.Ok -> sent.value
        }
        return if (response.isEmpty()) Outcome.Ok(Unit) else NtagResponses.checkAck(response)
    }
}
