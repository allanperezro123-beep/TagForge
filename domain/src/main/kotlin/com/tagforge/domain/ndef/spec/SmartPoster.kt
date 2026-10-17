package com.tagforge.domain.ndef.spec

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.RecordInterpreter
import com.tagforge.domain.ndef.ParsedRecord
import com.tagforge.domain.ndef.Tnf

/** NFC Forum Smart Poster (`Sp`): a record whose payload is itself an NDEF message holding a URI and optional title. */
object SmartPoster {

    val TYPE: Bytes = Bytes.of(0x53, 0x70)

    data class Content(val uri: String, val title: String?)

    fun record(inner: NdefMessage): NdefRecord =
        NdefRecord(Tnf.WELL_KNOWN, TYPE, payload = DefaultNdefCodec.encode(inner))

    /** Null when the payload is not a message with a URI record. */
    fun parse(record: NdefRecord): Content? {
        val inner = (DefaultNdefCodec.decode(record.payload) as? Outcome.Ok)?.value ?: return null
        val parsed = RecordInterpreter.interpret(inner)
        val uri = parsed.filterIsInstance<ParsedRecord.Uri>().firstOrNull()?.uri ?: return null
        val title = parsed.filterIsInstance<ParsedRecord.Text>().firstOrNull()?.text
        return Content(uri, title)
    }
}
