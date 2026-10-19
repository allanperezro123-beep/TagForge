package com.tagforge.domain.write

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefParseError
import com.tagforge.domain.ndef.Tnf
import com.tagforge.domain.ndef.Type2Tlv
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagTech

/**
 * Reads the NDEF message of a tag for the operations that are about to change it (overwrite question, write/clone
 * verification).
 *
 * An NTAG21x is read over the SAME raw NfcA channel the write itself uses. Going through the platform `Ndef`
 * technology instead means closing NfcA, connecting `Ndef`, and connecting NfcA again, and on real phones that switch
 * is where the tag was reported lost (erase, which never switches, worked on the same NTAG215). Any other tag has no
 * raw path and keeps using [TagSession.readNdef].
 *
 * Same contract as [TagSession.readNdef]: Ok(null) = formatted but empty, NotNdef = no NDEF structure, Malformed =
 * unparsable, AuthRequired = protected.
 */
internal object Type2Ndef {

    suspend fun read(session: TagSession, codec: NdefCodec = DefaultNdefCodec): TagResult<NdefMessage?> {
        val model = session.info.ntag
        val transceiver = session.transceiver(TagTech.NFC_A)
        if (model == null || transceiver == null) return session.readNdef()

        val area = when (val read = Type2Io.readCcAndUserArea(transceiver, model)) {
            is Outcome.Err -> return read
            is Outcome.Ok -> read.value
        }
        if (!area.hasCapabilityContainer) return Outcome.Err(TagError.NotNdef)
        val unwrapped = when (val result = Type2Tlv.unwrap(area.user)) {
            is Outcome.Ok -> result.value
            is Outcome.Err -> return Outcome.Err(
                if (result.error == NdefParseError.NoNdefTlv) TagError.NotNdef else TagError.Malformed(result.error.toString()),
            )
        }
        if (unwrapped.message.isEmpty()) return Outcome.Ok(null)
        val decoded = when (val result = codec.decode(unwrapped.message)) {
            is Outcome.Err -> return Outcome.Err(TagError.Malformed(result.error.toString()))
            is Outcome.Ok -> result.value
        }
        val records = decoded.records
        val onlyEmptyRecord = records.size == 1 && records[0].tnf == Tnf.EMPTY && records[0].payload.isEmpty()
        return Outcome.Ok(if (records.isEmpty() || onlyEmptyRecord) null else decoded)
    }
}
