package com.tagforge.domain.ndef

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome

/** Pure-Kotlin NDEF encoder/decoder (NFC Forum NDEF 1.0). Never throws for malformed input. */
object DefaultNdefCodec : NdefCodec {

    private const val FLAG_MB = 0x80
    private const val FLAG_ME = 0x40
    private const val FLAG_CF = 0x20
    private const val FLAG_SR = 0x10
    private const val FLAG_IL = 0x08
    private const val TNF_MASK = 0x07
    private const val SHORT_RECORD_MAX = 255

    override fun encode(message: NdefMessage): Bytes {
        val last = message.records.lastIndex
        return Bytes.concat(
            message.records.mapIndexed { index, record ->
                encodeRecord(record, messageBegin = index == 0, messageEnd = index == last)
            },
        )
    }

    override fun decode(bytes: Bytes): Outcome<NdefMessage, NdefParseError> {
        if (bytes.isEmpty()) return Outcome.Ok(NdefMessage.EMPTY)

        val reader = Reader(bytes)
        val records = ArrayList<NdefRecord>()
        var chunk: Chunk? = null
        var first = true

        while (!reader.atEnd) {
            val flags = reader.u8()
            val tnfCode = flags and TNF_MASK
            val tnf = Tnf.fromCode(tnfCode) ?: return Outcome.Err(NdefParseError.InvalidTnf(tnfCode))
            if (first && flags and FLAG_MB == 0) return Outcome.Err(NdefParseError.MissingMessageBegin)
            first = false

            val typeLength = reader.u8OrNull() ?: return TRUNCATED
            val payloadLength = (if (flags and FLAG_SR != 0) reader.u8OrNull() else reader.u32OrNull()) ?: return TRUNCATED
            val idLength = if (flags and FLAG_IL != 0) (reader.u8OrNull() ?: return TRUNCATED) else 0
            val type = reader.take(typeLength) ?: return TRUNCATED
            val id = reader.take(idLength) ?: return TRUNCATED
            val payload = reader.take(payloadLength) ?: return TRUNCATED

            val chunked = flags and FLAG_CF != 0
            val ended = flags and FLAG_ME != 0
            val open = chunk

            if (open == null) {
                if (tnf == Tnf.UNCHANGED) return Outcome.Err(NdefParseError.InvalidChunk)
                if (chunked) {
                    if (ended) return Outcome.Err(NdefParseError.InvalidChunk)
                    chunk = Chunk(tnf, type, id, mutableListOf(payload))
                    continue
                }
                records += NdefRecord(tnf, type, id, payload)
            } else {
                if (tnf != Tnf.UNCHANGED) return Outcome.Err(NdefParseError.InvalidChunk)
                open.parts += payload
                if (chunked) {
                    if (ended) return Outcome.Err(NdefParseError.InvalidChunk)
                    continue
                }
                records += NdefRecord(open.tnf, open.type, open.id, Bytes.concat(open.parts))
                chunk = null
            }
            if (ended) return Outcome.Ok(NdefMessage(records))
        }

        return Outcome.Err(if (chunk != null) NdefParseError.Truncated else NdefParseError.MissingMessageEnd)
    }

    private class Chunk(val tnf: Tnf, val type: Bytes, val id: Bytes, val parts: MutableList<Bytes>)

    /** Cursor over [bytes]; the `OrNull` variants report running out of data instead of throwing. */
    private class Reader(private val bytes: Bytes) {
        private var pos = 0
        val atEnd: Boolean get() = pos >= bytes.size

        fun u8(): Int = bytes[pos++]
        fun u8OrNull(): Int? = if (pos < bytes.size) bytes[pos++] else null

        fun u32OrNull(): Int? {
            if (pos + 4 > bytes.size) return null
            val value = (bytes[pos] shl 24) or (bytes[pos + 1] shl 16) or (bytes[pos + 2] shl 8) or bytes[pos + 3]
            pos += 4
            return if (value < 0) null else value   // lengths beyond Int range are necessarily truncated
        }

        fun take(count: Int): Bytes? {
            if (count < 0 || pos + count > bytes.size) return null
            return bytes.slice(pos, pos + count).also { pos += count }
        }
    }

    private val TRUNCATED: Outcome<NdefMessage, NdefParseError> = Outcome.Err(NdefParseError.Truncated)

    /** Encoded size of [record] on its own; independent of MB/ME so message size is the sum of these. */
    internal fun encodedSize(record: NdefRecord): Int {
        val lengthField = if (record.payload.size <= SHORT_RECORD_MAX) 1 else 4
        val idField = if (record.id.isEmpty()) 0 else 1
        return 1 + 1 + lengthField + idField + record.type.size + record.id.size + record.payload.size
    }

    private fun encodeRecord(record: NdefRecord, messageBegin: Boolean, messageEnd: Boolean): Bytes {
        require(record.type.size <= 0xFF) { "NDEF type length exceeds 255" }
        require(record.id.size <= 0xFF) { "NDEF id length exceeds 255" }

        val short = record.payload.size <= SHORT_RECORD_MAX
        var flags = record.tnf.code
        if (messageBegin) flags = flags or FLAG_MB
        if (messageEnd) flags = flags or FLAG_ME
        if (short) flags = flags or FLAG_SR
        if (!record.id.isEmpty()) flags = flags or FLAG_IL

        val header = ArrayList<Int>(8)
        header += flags
        header += record.type.size
        if (short) {
            header += record.payload.size
        } else {
            val n = record.payload.size
            header += (n ushr 24) and 0xFF
            header += (n ushr 16) and 0xFF
            header += (n ushr 8) and 0xFF
            header += n and 0xFF
        }
        if (!record.id.isEmpty()) header += record.id.size

        return Bytes.of(*header.toIntArray()) + record.type + record.id + record.payload
    }
}
