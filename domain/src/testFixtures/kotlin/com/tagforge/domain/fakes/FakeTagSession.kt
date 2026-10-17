package com.tagforge.domain.fakes

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefParseError
import com.tagforge.domain.ndef.Type2Tlv
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagIdentifier
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ntag.NtagResponses
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.tag.NdefInfo
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech

/**
 * [TagSession] backed by a [FakeNtag21x] (or nothing, for tags that are not Type 2). NDEF goes through the real
 * [Type2Tlv] and [DefaultNdefCodec], so read, write, verify, erase and clone are testable end to end on the JVM.
 */
class FakeTagSession(
    initialInfo: TagInfo,
    val ntag: FakeNtag21x? = null,
    private val codec: NdefCodec = DefaultNdefCodec,
) : TagSession {

    private var identified: TagInfo = initialInfo

    /** `ndef.isWritable` also follows the tag's Capability Container live, like a fresh Android `Ndef` connection would. */
    override val info: TagInfo
        get() {
            val tag = ntag ?: return identified
            val ndef = identified.ndef ?: return identified
            return identified.copy(ndef = ndef.copy(isWritable = ndef.isWritable && !tag.isNdefWriteLocked))
        }

    var closed: Boolean = false
        private set

    override suspend fun identify(): TagResult<TagInfo> {
        val transceiver = ntag
        if (identified.ntag == null && transceiver != null && TagTech.NFC_A in identified.techs) {
            when (val result = NtagIdentifier.identify(transceiver)) {
                is Outcome.Err -> return result
                is Outcome.Ok -> result.value?.let { model ->
                    identified = identified.copy(ntag = model, family = TagFamily.NTAG21X, ndef = identified.ndef ?: ndefInfoFor(model))
                }
            }
        }
        return Outcome.Ok(info)
    }

    override fun transceiver(tech: TagTech): TagTransceiver? = if (tech == TagTech.NFC_A) ntag else null

    override suspend fun readNdef(): TagResult<NdefMessage?> {
        val tag = ntag ?: return Outcome.Err(TagError.NotNdef)
        val area = when (val read = readUserArea(tag)) {
            is Outcome.Err -> return read
            is Outcome.Ok -> read.value
        }
        val unwrapped = when (val result = Type2Tlv.unwrap(area)) {
            is Outcome.Ok -> result.value
            is Outcome.Err -> return Outcome.Err(
                if (result.error == NdefParseError.NoNdefTlv) TagError.NotNdef else TagError.Malformed(result.error.toString()),
            )
        }
        if (unwrapped.message.isEmpty()) return Outcome.Ok(null)
        return when (val decoded = codec.decode(unwrapped.message)) {
            is Outcome.Ok -> Outcome.Ok(decoded.value)
            is Outcome.Err -> Outcome.Err(TagError.Malformed(decoded.error.toString()))
        }
    }

    override suspend fun writeNdef(message: NdefMessage): TagResult<Unit> {
        val tag = ntag ?: return Outcome.Err(TagError.NotNdef)
        if (info.ndef?.isWritable == false) return Outcome.Err(TagError.ReadOnly)
        val encoded = codec.encode(message)
        info.ndef?.let { if (encoded.size > it.maxSize) return Outcome.Err(TagError.CapacityExceeded(encoded.size, it.maxSize)) }
        return writeTlv(tag, encoded)
    }

    override suspend fun formatNdef(initial: NdefMessage?): TagResult<Unit> {
        val tag = ntag ?: return Outcome.Err(TagError.Unsupported("format"))
        if (info.ndef?.isWritable == false) return Outcome.Err(TagError.ReadOnly)
        return writeTlv(tag, initial?.let(codec::encode) ?: Bytes.EMPTY)
    }

    override suspend fun makeReadOnly(): TagResult<Unit> = Outcome.Err(TagError.Unsupported("makeReadOnly"))

    override suspend fun close() {
        closed = true
    }

    // ---- Type 2 plumbing --------------------------------------------------------------------------

    private suspend fun readUserArea(tag: FakeNtag21x): TagResult<Bytes> {
        val model = tag.model
        val chunks = mutableListOf<Bytes>()
        var page = model.userStart
        while (page <= model.userEnd) {
            val end = minOf(page + CHUNK_PAGES - 1, model.userEnd)
            val response = when (val sent = tag.transceive(NtagCommands.fastRead(page, end))) {
                is Outcome.Err -> return sent
                is Outcome.Ok -> sent.value
            }
            when (val checked = NtagResponses.checkData(response, (end - page + 1) * NtagCommands.PAGE_SIZE)) {
                is Outcome.Err -> return Outcome.Err(if (checked.error is TagError.Nak) TagError.AuthRequired else checked.error)
                is Outcome.Ok -> chunks += checked.value
            }
            page = end + 1
        }
        return Outcome.Ok(Bytes.concat(chunks))
    }

    /** Writes `leading TLVs + 03 len message FE` page by page, keeping any Lock Control TLV already on the tag. */
    private suspend fun writeTlv(tag: FakeNtag21x, message: Bytes): TagResult<Unit> {
        val current = when (val read = readUserArea(tag)) {
            is Outcome.Err -> return read
            is Outcome.Ok -> read.value
        }
        val leading = (Type2Tlv.unwrap(current) as? Outcome.Ok)?.value?.leadingBytes?.let { current.slice(0, it) }
            ?: tag.model.factoryLockControlTlv ?: Bytes.EMPTY
        val framed = Type2Tlv.wrap(message, leading)

        val padded = framed.toByteArray().let { it.copyOf(((it.size + 3) / 4) * 4) }
        var page = tag.model.userStart
        for (offset in padded.indices step NtagCommands.PAGE_SIZE) {
            val data = Bytes.copyOf(padded.copyOfRange(offset, offset + NtagCommands.PAGE_SIZE))
            val response = when (val sent = tag.transceive(NtagCommands.write(page, data))) {
                is Outcome.Err -> return sent
                is Outcome.Ok -> sent.value
            }
            NtagResponses.checkAck(response).let { if (it is Outcome.Err) return it }
            page++
        }
        return Outcome.Ok(Unit)
    }

    companion object {
        private const val CHUNK_PAGES = 16
        private val DEFAULT_ATQA = Bytes.of(0x00, 0x44)

        /** What Android's `Ndef.getMaxSize()` reports: CC size minus Lock Control TLV minus NDEF TLV header. */
        fun androidMaxSize(model: NtagModel): Int {
            val area = model.ccNdefSize - (model.factoryLockControlTlv?.size ?: 0)
            return area - if (area - 2 <= 254) 2 else 4
        }

        private fun ndefInfoFor(model: NtagModel) =
            NdefInfo(type = "org.nfcforum.ndef.type2", maxSize = androidMaxSize(model), isWritable = true, canMakeReadOnly = true)

        /** A session on [tag]. With `identified = false` the chip model must still be discovered by [identify]. */
        fun ntag(tag: FakeNtag21x, identified: Boolean = true): FakeTagSession {
            val techs = setOf(TagTech.NFC_A, TagTech.MIFARE_ULTRALIGHT, TagTech.NDEF)
            val info = if (identified) {
                TagInfo(tag.uid, techs, TagFamily.NTAG21X, tag.model, DEFAULT_ATQA, 0x00, null, ndefInfoFor(tag.model))
            } else {
                TagInfo(tag.uid, techs, TagFamily.UNKNOWN, null, DEFAULT_ATQA, 0x00, null, null)
            }
            return FakeTagSession(info, tag)
        }

        /** A tag with no Type 2 chip behind it (e.g. a MIFARE Classic card). */
        fun withoutNtag(
            family: TagFamily = TagFamily.MIFARE_CLASSIC,
            techs: Set<TagTech> = setOf(TagTech.NFC_A, TagTech.MIFARE_CLASSIC),
        ): FakeTagSession = FakeTagSession(
            TagInfo(Bytes.of(0xDE, 0xAD, 0xBE, 0xEF), techs, family, null, Bytes.of(0x00, 0x04), 0x08, null, null),
        )
    }
}
